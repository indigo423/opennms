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
package org.opennms.netmgt.config.wmi.agent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.opennms.core.xml.ValidateUsing;
import org.opennms.netmgt.config.utils.ConfigUtils;
import org.opennms.netmgt.config.wmi.WmiAgentConfig;

/**
 * Provides a mechanism for associating one or more
 *  specific IP addresses and/or IP address ranges with a
 *  set of WMI parms which will be used in place of the
 *  default values during WMI data collection.
 */
@XmlRootElement(name = "definition")
@XmlAccessorType(XmlAccessType.NONE)
@ValidateUsing("wmi-config.xsd")
public class Definition implements Serializable {
    private static final long serialVersionUID = 2L;

    @XmlAttribute(name = "retry")
    private Integer m_retry;

    @XmlAttribute(name = "timeout")
    private Integer m_timeout;

    @XmlAttribute(name = "username")
    private String m_username;

    @XmlAttribute(name = "domain")
    private String m_domain;

    @XmlAttribute(name = "password")
    private String m_password;

    /**
     * IP address range to which this definition
     *  applies.
     */
    @XmlElement(name = "range")
    private List<Range> m_ranges = new ArrayList<>();

    /**
     * Specific IP address to which this definition
     *  applies.
     */
    @XmlElement(name = "specific")
    private List<String> m_specifics = new ArrayList<>();

    /**
     * Match Octets (as in IPLIKE)
     */
    @XmlElement(name = "ip-match")
    private List<String> m_ipMatches = new ArrayList<>();

    public Integer getRetry() {
        return m_retry != null ? m_retry : WmiAgentConfig.DEFAULT_RETRIES;
    }

    public void setRetry(final Integer retry) {
        m_retry = retry;
    }

    public Integer getTimeout() {
        return m_timeout != null ? m_timeout : WmiAgentConfig.DEFAULT_TIMEOUT;
    }

    public void setTimeout(final Integer timeout) {
        m_timeout = timeout;
    }

    public Optional<String> getUsername() {
        return Optional.ofNullable(m_username);
    }

    public void setUsername(final String username) {
        m_username = ConfigUtils.normalizeString(username);
    }

    public Optional<String> getDomain() {
        return Optional.ofNullable(m_domain);
    }

    public void setDomain(final String domain) {
        m_domain = ConfigUtils.normalizeString(domain);
    }

    public Optional<String> getPassword() {
        return Optional.ofNullable(m_password);
    }

    public void setPassword(final String password) {
        m_password = ConfigUtils.normalizeString(password);
    }

    public List<Range> getRanges() {
        return m_ranges;
    }

    public void setRanges(final List<Range> ranges) {
        if (ranges == m_ranges) return;
        m_ranges.clear();
        if (ranges != null) m_ranges.addAll(ranges);
    }

    public void addRange(final Range range) {
        m_ranges.add(range);
    }

    public boolean removeRange(final Range range) {
        return m_ranges.remove(range);
    }

    public List<String> getSpecifics() {
        return m_specifics;
    }

    public void setSpecifics(final List<String> specifics) {
        if (specifics == m_specifics) return;
        m_specifics.clear();
        if (specifics != null) m_specifics.addAll(specifics);
    }

    public void addSpecific(final String specific) {
        m_specifics.add(specific);
    }

    public boolean removeSpecific(final String specific) {
        return m_specifics.remove(specific);
    }

    public List<String> getIpMatches() {
        return m_ipMatches;
    }

    public void setIpMatches(final List<String> ipMatches) {
        if (ipMatches == m_ipMatches) return;
        m_ipMatches.clear();
        if (ipMatches != null) m_ipMatches.addAll(ipMatches);
    }

    public void addIpMatch(final String ipMatch) {
        m_ipMatches.add(ipMatch);
    }

    public boolean removeIpMatch(final String ipMatch) {
        return m_ipMatches.remove(ipMatch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_retry, 
                            m_timeout, 
                            m_username, 
                            m_domain, 
                            m_password, 
                            m_ranges, 
                            m_specifics, 
                            m_ipMatches);
    }

    @Override
    public boolean equals(final Object obj) {
        if ( this == obj ) {
            return true;
        }

        if (obj instanceof Definition) {
            final Definition that = (Definition)obj;
            return Objects.equals(this.m_retry, that.m_retry)
                    && Objects.equals(this.m_timeout, that.m_timeout)
                    && Objects.equals(this.m_username, that.m_username)
                    && Objects.equals(this.m_domain, that.m_domain)
                    && Objects.equals(this.m_password, that.m_password)
                    && Objects.equals(this.m_ranges, that.m_ranges)
                    && Objects.equals(this.m_specifics, that.m_specifics)
                    && Objects.equals(this.m_ipMatches, that.m_ipMatches);
        }
        return false;
    }

}
