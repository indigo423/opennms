/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.alarmd.drools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.drools.core.ClockType;
import org.drools.core.time.SessionPseudoClock;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class focuses on providing a Drools context which loads a set of rules
 * from the filesystem and can be dynamically reloaded.
 *
 * It should not have knowledge of the underlying rules or facts that are inserted in the context.
 *
 * @author jwhite
 */
public class ManagedDroolsContext {
    private static final Logger LOG = LoggerFactory.getLogger(DroolsAlarmContext.class);

    private final File rulesFolder;
    private final String kbaseName;
    private final String kSessionName;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private boolean usePseudoClock = false;

    private boolean useManualTick = false;

    private ReleaseId releaseIdForContainerUsedByKieSession;

    private KieContainer kieContainer;

    private KieSession kieSession;

    private Thread thread;

    private SessionPseudoClock clock;

    /**
     * Ensure that this lock is fair so that ordering is respected.
     */
    private final ReentrantLock lock = new ReentrantLock(true);

    private Consumer<KieSession> onNewKiewSessionCallback;

    public ManagedDroolsContext(File rulesFolder, String kbaseName, String kSessionSuffixName) {
        this.rulesFolder = Objects.requireNonNull(rulesFolder);
        this.kbaseName = Objects.requireNonNull(kbaseName);
        this.kSessionName = String.format("%s-%s", kbaseName, Objects.requireNonNull(kSessionSuffixName));
    }

    public synchronized void start() {
        if (started.get()) {
            LOG.warn("The context for session {} is already started. Ignoring start request.", kSessionName);
            return;
        }

        // Build and deploy our ruleset
        final ReleaseId kieModuleReleaseId = buildKieModule();
        // Fire it up
        startWithModuleAndFacts(kieModuleReleaseId, Collections.emptyList());
    }

    public void onStart() {
        // pass
    }

    private void startWithModuleAndFacts(ReleaseId releaseId, List<Object> factObjects) {
        final KieServices ks = KieServices.Factory.get();
        kieContainer = ks.newKieContainer(releaseId);
        kieSession = kieContainer.newKieSession(kSessionName);

        if (usePseudoClock) {
            this.clock = kieSession.getSessionClock();
        } else {
            this.clock = null;
        }

        // Optionally restore any facts
        factObjects.forEach(factObject -> kieSession.insert(factObject));

        if (onNewKiewSessionCallback != null) {
            onNewKiewSessionCallback.accept(kieSession);
        }

        // Save the releaseId
        releaseIdForContainerUsedByKieSession = releaseId;

        // We're started!
        started.set(true);

        // Allow the base classes to seed the context before we start ticking
        onStart();

        if (!useManualTick) {
            thread = new Thread(() -> {
                while (started.get()) {
                    try {
                        LOG.debug("Firing until halt.");
                        kieSession.fireUntilHalt();
                    } catch (Exception e) {
                        // If we're supposed to be stopped, ignore the exception
                        if (!started.get()) {
                            LOG.error("Error occurred while firing rules. Waiting 30 seconds before starting to fire again.", e);
                            try {
                                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                            } catch (InterruptedException ex) {
                                LOG.warn("Interrupted while waiting to start firing rules again. Exiting thread.");
                                return;
                            }
                        } else {
                            LOG.info("Encountered exception while firing rules, but the engine is stopped. Exiting thread.");
                            return;
                        }
                    }
                }
            });
            thread.setName("DroolsSession-" + kSessionName);
            thread.start();
        }
    }

    public synchronized void reload() {
        if (!started.get()) {
            LOG.warn("The context for session {} is not yet started. Treating reload as a start request", kSessionName);
            start();
            return;
        }

        // Attempt to build and deploy the ruleset
        // If this fails, we'll throw an exception and abort the reload
        final ReleaseId releaseId = buildKieModule();

        // The rules we're successfully built and deployed

        // Let's halt the current engine
        started.set(false);
        if (!useManualTick) {
            kieSession.halt();
            try {
                thread.join(TimeUnit.MINUTES.toMillis(2));
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while waiting for session to halt. Aborting reload request.");
                return;
            }

            // The thread should be stopped, but we don't know for sure
            // Let's me a best effort to stop it before we proceed and start another one
            if (thread.isAlive()) {
                LOG.warn("Thread is still alive! Interrupting.");
                thread.interrupt();
            }
        }

        // Grab the facts
        final List<Object> factObjects = kieSession.getFactHandles().stream()
                .map(kieSession::getObject)
                .collect(Collectors.toList());

        // Dispose the session
        kieSession.dispose();

        // Remove the previous module
        if (releaseIdForContainerUsedByKieSession != null) {
            if (KieServices.Factory.get().getRepository().removeKieModule(releaseIdForContainerUsedByKieSession) != null) {
                LOG.info("Successfully removed previous KIE module with ID: {}.", releaseIdForContainerUsedByKieSession);
            } else {
                LOG.info("Previous KIE module was with ID: {} was already removed.", releaseIdForContainerUsedByKieSession);
            }
            releaseIdForContainerUsedByKieSession = null;
        }

        // Restart the engine
        startWithModuleAndFacts(releaseId, factObjects);
    }

    private ReleaseId buildKieModule() {
        final KieServices ks = KieServices.Factory.get();
        final KieFileSystem kfs = ks.newKieFileSystem();
        final ReleaseId id = generateReleaseId();

        final KieModuleModel module = ks.newKieModuleModel();
        final KieBaseModel base = module.newKieBaseModel(kbaseName);
        base.setDefault(true);
        base.addPackage("*");
        base.setEventProcessingMode(EventProcessingOption.STREAM);
        final KieSessionModel kieSessionModel = base.newKieSessionModel(kSessionName).setDefault(true)
                .setType(KieSessionModel.KieSessionType.STATEFUL);
        if (usePseudoClock) {
            kieSessionModel.setClockType(ClockTypeOption.get(ClockType.PSEUDO_CLOCK.getId()));
        }

        LOG.debug("kmodule.xml: {}", module.toXML());
        kfs.writeKModuleXML(module.toXML());
        kfs.generateAndWritePomXML(id);

        final List<File> rulesFiles;
        try {
            rulesFiles = getRulesFiles();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOG.info("Using rules files: {}", rulesFiles);
        for (File file : rulesFiles) {
            kfs.write("src/main/resources/" + file.getName(), ResourceFactory.newFileResource(file));
        }

        // Validate
        final KieBuilder kb = ks.newKieBuilder(kfs);
        kb.buildAll(); // kieModule is automatically deployed to KieRepository if successfully built.
        if (kb.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Build Errors:\n" + kb.getResults().toString());
        }

        LOG.info("Successfully built KIE module with ID: {}.", id);
        return id;
    }

    private List<File> getRulesFiles() throws IOException {
        final Path droolsRulesRoot = rulesFolder.toPath();
        if (!droolsRulesRoot.toFile().isDirectory()) {
            throw new IllegalStateException("Expected to find Drools rules for alarmd in '" + droolsRulesRoot
                    + "' but the path is not a directory! Aborting.");
        }
        return Files.find(droolsRulesRoot, 3, (path, attrs) -> attrs.isRegularFile()
                && path.toString().endsWith(".drl"))
                .map(Path::toFile)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    private static ReleaseId generateReleaseId() {
        final KieServices ks = KieServices.Factory.get();
        final String moduleName = UUID.randomUUID().toString();
        return ks.newReleaseId(ManagedDroolsContext.class.getPackage().getName(), moduleName, "1.0.0");
    }

    public void tick() {
        kieSession.fireAllRules();
    }

    public synchronized void stop() {
        started.set(false);
        if (kieSession != null) {
            kieSession.halt();
            kieSession = null;
        }
        if (kieContainer != null) {
            kieContainer.dispose();
            kieContainer = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public boolean isStarted() {
        return started.get();
    }

    public SessionPseudoClock getClock() {
        return clock;
    }

    public void setUsePseudoClock(boolean usePseudoClock) {
        this.usePseudoClock = usePseudoClock;
    }

    public void setUseManualTick(boolean useManualTick) {
        this.useManualTick = useManualTick;
    }

    public void setOnNewKiewSessionCallback(Consumer<KieSession> onNewKiewSessionCallback) {
        this.onNewKiewSessionCallback = onNewKiewSessionCallback;
    }

    public KieSession getKieSession() {
        return kieSession;
    }
}
