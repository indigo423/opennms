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
package org.opennms.netmgt.correlation.drools;

import static org.opennms.core.utils.InetAddressUtils.addr;

import org.junit.Test;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.xml.event.Event;

public class LocationMonitorRulesIT extends CorrelationRulesTestCase {

    private static final String WS_OUTAGE_UEI = "uei.opennms.org/correlation/perspective/wideSpreadOutage";
    private static final String WS_RESOLVED_UEI = "uei.opennms.org/correlation/perspective/wideSpreadOutageResolved";
    private static final String SERVICE_FLAPPING_UEI = "uei.opennms.org/correlation/serviceFlapping";

    @Test
    public void testWideSpreadLocationMonitorOutage() throws Exception {

        resetAnticipated();

        DroolsCorrelationEngine engine = findEngineByName("locationMonitorRules");

        anticipateWideSpreadOutageEvent();

        // received outage events for all monitors
        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 8));
        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 9));

        // expect memory to contain only the single 'affliction' for this service
        // and the flap tracker for each monitor
        m_anticipatedMemorySize = 4;

        verify(engine);

        anticipateWideSpreadOutageResolvedEvent();

        // received outage events for all monitors
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 9));
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 8));

        // expect the flap tracker to remain
        m_anticipatedMemorySize = 6;

        verify(engine);

        // need to time the flap trackers out
        Thread.sleep(1100);

        m_anticipatedMemorySize = 0;

        verify(engine);

    }

    @Test
    public void testSingleLocationMonitorOutage() throws Exception {

        resetAnticipated();

        DroolsCorrelationEngine engine = findEngineByName("locationMonitorRules");

        // receive outage event for only a single monitor
        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));

        // expect memory to contain only the single 'application' for this service
        m_anticipatedMemorySize = 2;

        verify(engine);

        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));

        m_anticipatedMemorySize = 2;

        verify(engine);

        // let flaps time out
        Thread.sleep(1100);

        m_anticipatedMemorySize = 0;

        verify(engine);
    }

    @Test
    public void testDoubleLocationMonitorOutage() throws Exception {

        resetAnticipated();

        DroolsCorrelationEngine engine = findEngineByName("locationMonitorRules");

        // receive outage event for only a single monitor
        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 8));

        // expect memory to contain only the single 'application' for this service
        m_anticipatedMemorySize = 3;

        verify(engine);

        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 8));

        m_anticipatedMemorySize = 4;

        verify(engine);

        Thread.sleep(1100);

        m_anticipatedMemorySize = 0;

        verify(engine);
    }

    @Test
    public void testFlappingMonitor() throws Exception {

        resetAnticipated();

        DroolsCorrelationEngine engine = findEngineByName("locationMonitorRules");

        /* 
         * for testing the flap rules detect 3 outages that occur within 1000 millis
         * when this happens a serviceflapping event is produced
         */
        anticipateServiceFlappingEvent();

        // receive outage event for only a single monitor
        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));

        Thread.sleep(100);

        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));

        Thread.sleep(100);

        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));

        // expect an affliction and a flap for each outage
        m_anticipatedMemorySize = 4;

        // ensure the correct objects are in memory and the service flapping event has been sent
        verify(engine);

        // wait for one of the flaps to expire - this is kind  of tight an a unresponsive may 
        // not wake up in time and the second flap could be expired also
        Thread.sleep(810);

        m_anticipatedMemorySize = 3;

        verify(engine);

        // now all of the flaps should be expired
        Thread.sleep(200);

        m_anticipatedMemorySize = 0;

        verify(engine);

        anticipateServiceFlappingEvent();

        // cause another very fast flapping situtation
        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));

        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));

        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));
        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));

        m_anticipatedMemorySize = 4;

        verify(engine);

        // wait for it to expire
        Thread.sleep(1100);

        // now there should be nothing
        m_anticipatedMemorySize = 0;

        verify(engine);

    }

    @Test
    public void testDontFlapWhenOnlyTwoOutages() throws Exception {

        resetAnticipated();

        DroolsCorrelationEngine engine = findEngineByName("locationMonitorRules");

        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "AVAIL", 7));

        Thread.sleep(50);

        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));

        Thread.sleep(50);

        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "AVAIL", 7));

        Thread.sleep(50);

        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));

        Thread.sleep(50);

        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "AVAIL", 7));

        Thread.sleep(50);

        engine.correlate(createPerspectiveNodeLostServiceEvent(1, "192.168.1.1", "HTTP", 7));

        Thread.sleep(50);

        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "AVAIL", 7));

        Thread.sleep(50);

        engine.correlate(createPerspectiveNodeRegainedServiceEvent(1, "192.168.1.1", "HTTP", 7));

        m_anticipatedMemorySize = 6;

        Thread.sleep(100);

        verify(engine);

        Thread.sleep(1000);

        m_anticipatedMemorySize = 0;

        verify(engine);

    }

    private void anticipateWideSpreadOutageEvent() {
        anticipate(createWideSpreadOutageEvent());
    }

    private Event createWideSpreadOutageEvent() {
        return new EventBuilder(WS_OUTAGE_UEI, "Drools")
                .setNodeid(1).setInterface(addr("192.168.1.1"))
                .setService("HTTP")
                .getEvent();
    }

    private void anticipateWideSpreadOutageResolvedEvent() {
        anticipate(createWideSpreadOutageResolvedEvent());
    }

    private Event createWideSpreadOutageResolvedEvent() {
        EventBuilder bldr = new EventBuilder(WS_RESOLVED_UEI, "Drools");
        bldr.setNodeid(1).setInterface(addr("192.168.1.1"))
                .setService("HTTP");

        Event event = bldr.getEvent();
        return event;
    }

    private void anticipateServiceFlappingEvent() {
        anticipate(createServiceFlappingEvent());
    }

    private Event createServiceFlappingEvent() {
        return new EventBuilder(SERVICE_FLAPPING_UEI, "Drools")
                .setNodeid(1).setInterface(addr("192.168.1.1"))
                .setService("HTTP")
                .getEvent();
    }

}
