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
package org.opennms.features.eifadapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opennms.features.eifadapter.EifParser.parseEifSlots;
import static org.opennms.features.eifadapter.EifParser.translateEifToOpenNMS;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.dao.mock.MockMonitoringLocationDao;
import org.opennms.netmgt.dao.mock.MockNodeDao;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.xbill.DNS.Address;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-mockDao.xml"
})
@JUnitConfigurationEnvironment
public class EifTranslatorTest {

    @Autowired
    private MockMonitoringLocationDao m_locationDao;

    @Autowired
    private MockNodeDao m_nodeDao;

    @Before
    public void setUp() throws UnknownHostException {
        // Enable DEBUG logging
        MockLogAppender.setupLogging();

        OnmsNode fqhostnameNode = new OnmsNode(m_locationDao.getDefaultLocation(), "localhost.localdomain");
        OnmsNode shortnameNode = new OnmsNode(m_locationDao.getDefaultLocation(), "localhost");
        OnmsNode originNode = new OnmsNode(m_locationDao.getDefaultLocation(), "10.0.0.7");
        OnmsNode localhostIpNode = null;
        try {
            final InetAddress localAddr = Address.getByName("localhost");
            System.err.println("localAddr=" + localAddr);
            localhostIpNode = new OnmsNode(m_locationDao.getDefaultLocation(), InetAddressUtils.str(localAddr));
        } catch (final Exception e) {
            localhostIpNode = new OnmsNode(m_locationDao.getDefaultLocation(), "127.0.0.1");
        }

        fqhostnameNode.setForeignSource("eifTestSource");
        fqhostnameNode.setForeignId("eifTestId");
        shortnameNode.setForeignSource("eifTestSource");
        shortnameNode.setForeignId("eifTestId");
        originNode.setForeignSource("eifTestSource");
        originNode.setForeignId("eifTestId");
        localhostIpNode.setForeignId("eifTestLocalhostIp");

        fqhostnameNode.setId(1);
        shortnameNode.setId(2);
        originNode.setId(3);
        localhostIpNode.setId(4);

        m_nodeDao.saveOrUpdate(fqhostnameNode);
        m_nodeDao.saveOrUpdate(shortnameNode);
        m_nodeDao.saveOrUpdate(originNode);
        m_nodeDao.saveOrUpdate(localhostIpNode);
        m_nodeDao.flush();
    }

    @Test
    public void testCanTranslateSimpleEifEvent() {
        String incomingEif = "<START>>......................LL.....EIF_EVENT_TYPE_A;cms_hostname='htems_host';"
                +"cms_port='3661';integration_type='N';master_reset_flag='';appl_label='';"
                +"situation_name='DummyMonitoringSituation';situation_type='S';situation_origin='dummyHost:08';"
                +"situation_time='07/22/2016 14:05:36.000';situation_status='Y';situation_thrunode='REMOTE_teps_host';"
                +"situation_displayitem='';source='EIF_TEST';sub_source='dummyHost:08';hostname='dummyHost';"
                +"origin='10.0.0.7';adapter_host='dummyHost';date='07/22/2016';severity='WARNING';"
                +"msg='My Dummy Event Message';situation_eventdata='~';END";
        Event e = translateEifToOpenNMS(m_nodeDao, new StringBuilder(incomingEif)).get(0);
        assertEquals("Severity must be 'Warning'","Warning",e.getSeverity());
        assertEquals("uei.opennms.org/vendor/IBM/EIF/EIF_EVENT_TYPE_A",e.getUei());
        assertEquals("DummyMonitoringSituation",e.getParm("situation_name").getValue().getContent());
    }

    @Test
    public void testCanParseEifSlots() {
        String eifBody = "integration_type='N';master_reset_flag='';appl_label='';"
                +"situation_name='DummyMonitoringSituation';situation_type='S';"
                +"situation_origin='dummyHost:08';situation_time='07/22/2016 14:05:36.000';situation_status='Y';"
                +"situation_thrunode='REMOTE_teps_host';situation_displayitem='';source='EIF_TEST';"
                +"sub_source='dummyHost:08';hostname='dummyHost';origin='10.0.0.7';adapter_host='dummyHost';"
                +"date='07/22/2016';severity='WARNING';msg='My Dummy Event Message';situation_eventdata='~';";
        Map<String, String> eifSlotMap = parseEifSlots(eifBody);
        assertEquals("EIF_TEST",eifSlotMap.get("source"));
        assertEquals("dummyHost",eifSlotMap.get("adapter_host"));
        assertEquals("REMOTE_teps_host",eifSlotMap.get("situation_thrunode"));
    }

    @Test
    public void testCanParseEifSlotsWithEmbeddedSemicolons() {
        String eifBody = "cms_hostname='hubtems01';cms_port='3661';"
                +"integration_type='U';master_reset_flag='';appl_label='';situation_name='Situation 01';"
                +"situation_type='S';situation_origin='';situation_time='07/28/2016 12:19:11.000';situation_status='P';"
                +"situation_thrunode='REMOTE_teps_host';situation_fullname='Situation 01';situation_displayitem='';"
                +"source='EIF_TEST';sub_source='';hostname='';origin='';adapter_host='dummyHost';date='07/28/2016';"
                +"severity='CRITICAL';IncidentSupportTeam='Server Support Testing';"
                +"semicolon_test='this is a test; of semicolons in; slot values';"
                +"onClose_msg='Event closed. OpenNMS EIF Testing.';onClose_severity='WARNING';send_delay='6';"
                +"msg='This is a test of EIF for OpenNMS';situation_eventdata='~';END";
        Map<String, String> eifSlotMap = parseEifSlots(eifBody);
        assertEquals("EIF_TEST",eifSlotMap.get("source"));
        assertEquals("dummyHost",eifSlotMap.get("adapter_host"));
        assertEquals("REMOTE_teps_host",eifSlotMap.get("situation_thrunode"));
    }

    @Test
    public void testCanParseEifEventWithSemicolonInSlot() {
        String incomingEif = ".<START>>......................LL.....EIF_TEST_EVENT_TYPE_A;cms_hostname='hubtems01';cms_port='3661';"
                +"integration_type='U';master_reset_flag='';appl_label='';situation_name='Situation 01';"
                +"situation_type='S';situation_origin='';situation_time='07/28/2016 12:19:11.000';situation_status='P';"
                +"situation_thrunode='REMOTE_teps_host';situation_fullname='Situation 01';situation_displayitem='';"
                +"source='EIF_TEST';sub_source='';hostname='';origin='';adapter_host='';date='07/28/2016';"
                +"severity='CRITICAL';IncidentSupportTeam='Server Support Testing';"
                +"semicolon_test='this is a test; of semicolons in; slot values';"
                +"onClose_msg='Event closed. OpenNMS EIF Testing.';onClose_severity='WARNING';send_delay='6';"
                +"msg='This is a test of EIF for OpenNMS';situation_eventdata='~';END";
        Event e = translateEifToOpenNMS(m_nodeDao, new StringBuilder(incomingEif)).get(0);
        assertEquals("Severity must be 'Warning'","Major",e.getSeverity());
        assertEquals("uei.opennms.org/vendor/IBM/EIF/EIF_TEST_EVENT_TYPE_A",e.getUei());
        assertEquals("Situation 01",e.getParm("situation_name").getValue().getContent());
        assertEquals("~",e.getParm("situation_eventdata").getValue().getContent());
    }

    @Test
    public void testCanParseMultipleEvents() {
        String incomingEif_1 = ".<START>>.......................0.....EIF_TEST_EVENT_TYPE_A;cms_hostname='hubtems01';"
                +"cms_port='3661';integration_type='N';master_reset_flag='';appl_label='';"
                +"situation_name='DummyMonitoringSituation';situation_type='S';situation_origin='managedsystem01:08';"
                +"situation_time='07/22/2016 14:05:36.000';situation_status='Y';situation_thrunode='REMOTE_rtems01';"
                +"situation_displayitem='';source='EIF';sub_source='managedsystem01:08';hostname='managedsystem01';"
                +"origin='10.0.0.1';adapter_host='managedsystem01';date='07/22/2016';severity='WARNING';"
                +"msg='EIF Test Message 1';situation_eventdata='~';END";
        String incomingEif_2 = ".<START>>......................L......EIF_TEST_EVENT_TYPE_B;cms_hostname='hubtems01';"
                +"cms_port='3661';integration_type='U';master_reset_flag='';appl_label='';"
                +"situation_name='DummyMonitoringSituation';situation_type='S';situation_origin='managedsystem02:LZ';"
                +"situation_time='07/22/2016 14:07:52.000';situation_status='Y';situation_thrunode='HUB_hubtems01';"
                +"situation_displayitem='';source='EIF';sub_source='managedsystem02:LZ';hostname='managedsystem02';"
                +"origin='10.0.0.2';adapter_host='managedsystem02';date='07/22/2016';severity='HARMLESS';"
                +"msg='EIF_Heartbeat';situation_eventdata='Day_Of_Month=22;Day_Of_Week=06;Hours=15;Minutes=50;"
                +"Month_Of_Year=07;System_Name=managedsystem02:LZ;Seconds=25;Timestamp=1160722155025000~';END";
        String incomingEif_3 = ".<START>>......................8......EIF_TEST_EVENT_TYPE_A;cms_hostname='hubtems01';"
                +"cms_port='3661';integration_type='U';master_reset_flag='';appl_label='';"
                +"situation_name='DummyMonitoringSituation';situation_type='S';situation_origin='managedsystem03:LZ';"
                +"situation_time='07/22/2016 14:08:07.000';situation_status='Y';situation_thrunode='REMOTE_hubtems02';"
                +"situation_displayitem='';source='EIF';sub_source='managedsystem03:LZ';hostname='managedsystem03';"
                +"origin='10.0.0.3';adapter_host='managedsystem03';date='07/22/2016';severity='CRITICAL';"
                +"SupportGroup='Server Support';custom_slot0='Server Support';priority='2';"
                +"msg='EIF Test Message 3';situation_eventdata='test_command;"
                +"System_Name=managedsystem03:LZ~test_command;System_Name=managedsystem03:LZ~';END";
        String multipleEif = new StringBuilder(incomingEif_1).append("\n").append(incomingEif_2).append("\n").
                append(incomingEif_3).append("\n").toString();

        List<Event> events = translateEifToOpenNMS(m_nodeDao, new StringBuilder(multipleEif));
        assertTrue("Event list must not be null", events != null);
        for (Event event : events) {
            System.out.println("Evaluating UEI regex on "+event.getUei());
            assertTrue("UEI must match regex.",event.getUei().matches("^uei.opennms.org/vendor/IBM/EIF/EIF_TEST_EVENT_TYPE_\\w$"));
            System.out.println("Checking value of parm situation_name: "+event.getParm("situation_name").getValue().getContent());
            assertEquals("DummyMonitoringSituation",event.getParm("situation_name").getValue().getContent());
        }
    }

    @Test
    public void testCanConnectEifEventToNodeWithFqhostname() {
        String incomingEif = "<START>>......................LL.....EIF_EVENT_TYPE_A;cms_hostname='htems_host';"
                +"cms_port='3661';integration_type='N';master_reset_flag='';appl_label='';"
                +"situation_name='DummyMonitoringSituation';situation_type='S';situation_origin='dummyHost:08';"
                +"situation_time='07/22/2016 14:05:36.000';situation_status='Y';situation_thrunode='REMOTE_teps_host';"
                +"situation_displayitem='';source='EIF_TEST';sub_source='dummyHost:08';"
                +"fqhostname='localhost.localdomain';hostname='dummyHost';origin='127.0.0.1';adapter_host='dummyHost';"
                +"date='07/22/2016';severity='WARNING';msg='My Dummy Event Message';situation_eventdata='~';END";
        Event e = translateEifToOpenNMS(m_nodeDao, new StringBuilder(incomingEif)).get(0);
        assertEquals("NodeId "+e.getNodeid()+" must equal 1","1",e.getNodeid().toString());
    }

    @Test
    @Ignore
    public void testCanConnectEifEventToNodeWithHostname() {
        String incomingEif = "<START>>......................LL.....EIF_EVENT_TYPE_A;cms_hostname='htems_host';"
                +"cms_port='3661';integration_type='N';master_reset_flag='';appl_label='';"
                +"situation_name='DummyMonitoringSituation';situation_type='S';situation_origin='dummyHost:08';"
                +"situation_time='07/22/2016 14:05:36.000';situation_status='Y';situation_thrunode='REMOTE_teps_host';"
                +"situation_displayitem='';source='EIF_TEST';sub_source='dummyHost:08';"
                +"fqhostname='';hostname='localhost';origin='127.0.0.1';adapter_host='dummyHost';"
                +"date='07/22/2016';severity='WARNING';msg='My Dummy Event Message';situation_eventdata='~';END";
        Event e = translateEifToOpenNMS(m_nodeDao, new StringBuilder(incomingEif)).get(0);
        // nodeId will be 4 if 'localhost' fails to resolve, and we fall back to using the IP address.
        assertTrue("NodeId " + e.getNodeid() + " must be either 2 or 4",
                e.getNodeid() == 2 || e.getNodeid() == 4);
    }

    @Test
    public void testCanConnectEifEventToNodeWithOrigin() {
        String incomingEif = "<START>>......................LL.....EIF_EVENT_TYPE_A;cms_hostname='htems_host';"
                +"cms_port='3661';integration_type='N';master_reset_flag='';appl_label='';"
                +"situation_name='DummyMonitoringSituation';situation_type='S';situation_origin='dummyHost:08';"
                +"situation_time='07/22/2016 14:05:36.000';situation_status='Y';situation_thrunode='REMOTE_teps_host';"
                +"situation_displayitem='';source='EIF_TEST';sub_source='dummyHost:08';"
                +"fqhostname='';hostname='';origin='10.0.0.7';adapter_host='dummyHost';"
                +"date='07/22/2016';severity='WARNING';msg='My Dummy Event Message';situation_eventdata='~';END";
        Event e = translateEifToOpenNMS(m_nodeDao, new StringBuilder(incomingEif)).get(0);
        assertEquals("NodeId "+e.getNodeid()+" must equal 3","3",e.getNodeid().toString());
    }
}
