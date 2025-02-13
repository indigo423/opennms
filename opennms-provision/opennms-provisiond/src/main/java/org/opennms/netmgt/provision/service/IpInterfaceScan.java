/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.provision.service;

import static org.opennms.core.utils.InetAddressUtils.str;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.MapContext;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.opennms.core.tasks.AbstractTask;
import org.opennms.core.tasks.BatchTask;
import org.opennms.core.tasks.Callback;
import org.opennms.core.tasks.RunInBatch;
import org.opennms.core.utils.IPLike;
import org.opennms.core.utils.jexl.OnmsJexlEngine;
import org.opennms.netmgt.model.OnmsMetaData;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.opennms.netmgt.provision.DetectorRequestBuilder;
import org.opennms.netmgt.provision.persist.foreignsource.PluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;

/**
 * <p>IpInterfaceScan class.</p>
 *
 * @author ranger
 * @version $Id: $
 */
public class IpInterfaceScan implements RunInBatch {
    private static final Logger LOG = LoggerFactory.getLogger(IpInterfaceScan.class);
    public static final String METADATA_CONTEXT_DETECTOR = "detector";

    private final ProvisionService m_provisionService;
    private final InetAddress m_address;
    private final Integer m_nodeId;
    private final String m_foreignSource;
    private final OnmsMonitoringLocation m_location;
    private final Span m_parentSpan;
    private Span m_span;

    /**
     * <p>Constructor for IpInterfaceScan.</p>
     *
     * @param nodeId a {@link java.lang.Integer} object.
     * @param address a {@link java.net.InetAddress} object.
     * @param foreignSource a {@link java.lang.String} object.
     * @param location a {@link org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation} object.
     * @param provisionService a {@link org.opennms.netmgt.provision.service.ProvisionService} object.
     */
    public IpInterfaceScan(final Integer nodeId, final InetAddress address, final String foreignSource, final OnmsMonitoringLocation location, final ProvisionService provisionService, final Span span) {
        m_nodeId = nodeId;
        m_address = address;
        m_foreignSource = foreignSource;
        m_location = location;
        m_provisionService = provisionService;
        m_parentSpan = span;
    }

    /**
     * <p>getForeignSource</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getForeignSource() {
        return m_foreignSource;
    }

    /**
     * <p>getNodeId</p>
     *
     * @return a {@link java.lang.Integer} object.
     */
    public Integer getNodeId() {
        return m_nodeId;
    }

    public OnmsMonitoringLocation getLocation() {
        return m_location;
    }

    /**
     * <p>getAddress</p>
     *
     * @return a {@link java.net.InetAddress} object.
     */
    public InetAddress getAddress() {
        return m_address;
    }

    /**
     * <p>getProvisionService</p>
     *
     * @return a {@link org.opennms.netmgt.provision.service.ProvisionService} object.
     */
    public ProvisionService getProvisionService() {
        return m_provisionService;
    }

    /**
     * <p>toString</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("address", m_address)
                .append("foreign source", m_foreignSource)
                .append("node ID", m_nodeId)
                .append("location", m_location != null ? m_location.getLocationName() : null)
                .toString();
    }

    /**
     * <p>servicePersister</p>
     * 
     * @param currentPhase a {@link org.opennms.core.tasks.BatchTask} object.
     * @return a {@link org.opennms.core.tasks.Callback} object.
     */
    public static Callback<Boolean> servicePersister(final BatchTask currentPhase, final ProvisionService service, final PluginConfig detectorConfig, final int nodeId, final InetAddress address, final CompletableFuture<Boolean> future) {
        return new Callback<Boolean>() {
            @Override
            public void accept(final Boolean serviceDetected) {
                final String hostAddress = str(address);
                final String serviceName = detectorConfig.getName();
                LOG.info("Attempted to detect service {} on address {}: {}", serviceName, hostAddress, serviceDetected);
                if (serviceDetected) {

                    /*
                     * TODO: Convert this sequence into a chain of CompletableFutures 
                     */
                    currentPhase.getBuilder().addSequence(
                            new RunInBatch() {
                                @Override
                                public void run(final BatchTask batch) {
                                    if ("SNMP".equals(serviceName)) {
                                        service.setIsPrimaryFlag(nodeId, hostAddress);
                                    }
                                }
                            },
                            new RunInBatch() {
                                @Override
                                public void run(final BatchTask batch) {
                                    final var metaData = detectorConfig.getParameterMap().entrySet().stream()
                                            .map(attribute -> new OnmsMetaData(METADATA_CONTEXT_DETECTOR, attribute.getKey(), attribute.getValue()))
                                            .collect(Collectors.toList());

                                    service.addMonitoredService(nodeId, hostAddress, serviceName, null, metaData);
                                }
                            },
                            new RunInBatch() {
                                @Override
                                public void run(final BatchTask batch) {
                                    // NMS-3906
                                    service.updateMonitoredServiceState(nodeId, hostAddress, serviceName);
                                }
                            });
                }
                future.complete(serviceDetected);
            }

            @Override
            public Boolean apply(final Throwable t) {
                LOG.info("Exception occurred while trying to detect service {} on address {}", detectorConfig.getName(), str(address), t);
                return false;
            }
        };
    }

    protected static AbstractTask createDetectorTask(final BatchTask currentPhase, final ProvisionService service, final PluginConfig detectorConfig, final int nodeId, final InetAddress address, final OnmsMonitoringLocation location, Span span, final CompletableFuture<Boolean> future) {
        return currentPhase.getCoordinator().createTask(currentPhase, new DetectorRunner(service, detectorConfig, nodeId, address, location, span), servicePersister(currentPhase, service, detectorConfig, nodeId, address, future));
    }

    /** {@inheritDoc} */
    @Override
    public void run(final BatchTask currentPhase) {

        m_span = getProvisionService().buildAndStartSpan("IpInterfaceScan", m_parentSpan.context());
        m_span.setTag(ProvisionService.IP_ADDRESS, str(getAddress()));
        m_span.setTag(ProvisionService.LOCATION, getLocation().getLocationName());

        // This call returns a collection of new ServiceDetector instances
        final Collection<PluginConfig> detectorConfigs = getProvisionService().getDetectorsForForeignSource(getForeignSource() == null ? "default" : getForeignSource());

        LOG.info("Detecting services for node {}/{} on address {}: found {} detectors", getNodeId(), getForeignSource(), str(getAddress()), detectorConfigs.size());
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (final PluginConfig detectorConfig : detectorConfigs) {
            if (shouldDetect(detectorConfig, getAddress())) {
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                futures.add(future);
                currentPhase.add(createDetectorTask(currentPhase, getProvisionService(), detectorConfig, getNodeId(), getAddress(), getLocation(), m_span, future));
            }
        }
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
        allFutures.whenComplete(((aVoid, throwable) -> {
            m_span.finish();
        }));
    }

    protected static boolean shouldDetect(final PluginConfig detectorConfig, final InetAddress address) {
        String ipMatch = detectorConfig.getParameter("ipMatch");
        if (ipMatch  == null || ipMatch.trim().isEmpty()) return true; // Execute the detector if the ipMatch is not provided.
        // Regular Expression Matching
        if (ipMatch.startsWith("~")) {
            return address.getHostAddress().matches(ipMatch.substring(1));
        }
        // Expression based IPLIKE Matching
        return isIpMatching(address, ipMatch);
    }

    protected static boolean isIpMatching(final InetAddress ip, final String expr) {
        try {
            OnmsJexlEngine parser = new OnmsJexlEngine();
            parser.white(IPLike.class.getName());
            parser.white(InetAddress.class.getName());
            Expression e = parser.createExpression(generateExpr(expr));
            final Map<String,Object> context = new HashMap<String,Object>();
            context.put("iplike", IPLike.class);
            context.put("ipaddr", ip.getHostAddress());
            Boolean out = (Boolean) e.evaluate(new MapContext(context));
            return out;
        } catch (Exception e) {
            LOG.error("Can't process rule '{}' while checking IP {}.", expr, ip, e);
            return false;
        }
    }

    protected static String generateExpr(final String basicExpr) {
        LOG.debug("generateExpr: original expression {}", basicExpr);
        String data = basicExpr;
        Pattern p = Pattern.compile("[0-9a-f:.,\\-*]+");
        Matcher m = p.matcher(data);
        while (m.find()) {
            data = data.replace(m.group(), "iplike.matches(ipaddr,'" + m.group() + "')");
        }
        LOG.debug("generateExpr: computed expression {}", data);
        return data;
    }

}
