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
package org.opennms.features.kafka.producer.datasync;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opennms.features.kafka.producer.model.OpennmsModelProtos;
import org.opennms.netmgt.model.OnmsAlarm;

/**
 * This interface was created to be able to expose the methods on
 * {@link KafkaAlarmDataSync} to the {@link org.opennms.features.kafka.producer.shell.SyncAlarms}
 * shell command.
 */
public interface AlarmDataStore {

    void init() throws IOException;

    void destroy();

    boolean isEnabled();

    boolean isReady();

    Map<String, OpennmsModelProtos.Alarm> getAlarms();

    OpennmsModelProtos.Alarm getAlarm(String reductionKey);

    AlarmSyncResults handleAlarmSnapshot(List<OnmsAlarm> alarms);

    void setStartWithCleanState(boolean startWithCleanState);

}
