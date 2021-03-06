/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2007-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.threshd;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.opennms.netmgt.config.threshd.ThresholdType;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.threshd.api.ThresholdingSession;
import org.opennms.netmgt.xml.event.Event;
import org.springframework.util.Assert;

/**
 * Implements a relative change threshold check.  A 'value' setting of
 * less than 1.0 means that a threshold will fire if the current value
 * is less than or equal to the previous value multiplied by the 'value'
 * setting.  A 'value' setting greater than 1.0 causes the threshold to
 * fire if the current value is greater than or equal to the previous
 * value multiplied by the 'value' setting.  A 'value' setting of 1.0
 * (unity) is not allowed, as it represents no change.  Zero valued
 * samples (0.0) are ignored, as 0.0 multiplied by anything is 0.0 (if
 * they were not ignored, an interface that gets no traffic would always
 * trigger a threshold, for example).
 *
 * @author ranger
 * @version $Id: $
 */
public class ThresholdEvaluatorRelativeChange implements ThresholdEvaluator {
    
    private static final ThresholdType TYPE = ThresholdType.RELATIVE_CHANGE;

    /** {@inheritDoc} */
    @Override
    public ThresholdEvaluatorState getThresholdEvaluatorState(BaseThresholdDefConfigWrapper threshold, ThresholdingSession thresholdingSession) {
        return new ThresholdEvaluatorStateRelativeChange(threshold, thresholdingSession);
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsType(ThresholdType type) {
        return TYPE.equals(type);
    }
    
    public static class ThresholdEvaluatorStateRelativeChange extends AbstractThresholdEvaluatorState<ThresholdEvaluatorStateRelativeChange.State> {
        private BaseThresholdDefConfigWrapper m_thresholdConfig;

        static class State implements Serializable {
            private static final long serialVersionUID = 1L;
            private double m_multiplier;
            private double m_lastSample = 0.0;
            private double m_previousTriggeringSample;

            @Override
            public String toString() {
                return "multiplier=" + m_multiplier +
                        "\nlastSample=" + m_lastSample +
                        "\npreviousTriggeringSample=" + m_previousTriggeringSample;
            }
        }

        public ThresholdEvaluatorStateRelativeChange(BaseThresholdDefConfigWrapper threshold, ThresholdingSession thresholdingSession) {
            super(threshold, thresholdingSession);
            setThresholdConfig(threshold);
        }

        @Override
        protected void initializeState() {
            state = new State();
        }

        private void setThresholdConfig(BaseThresholdDefConfigWrapper thresholdConfig) {
            Assert.notNull(thresholdConfig.getType(), "threshold must have a 'type' value set");
            Assert.notNull(thresholdConfig.getDatasourceExpression(), "threshold must have a 'ds-name' value set");
            Assert.notNull(thresholdConfig.getDsType(), "threshold must have a 'ds-type' value set");
            Assert.isTrue(thresholdConfig.hasValue(), "threshold must have a 'value' value set");
            Assert.isTrue(thresholdConfig.hasRearm(), "threshold must have a 'rearm' value set");
            Assert.isTrue(thresholdConfig.hasTrigger(), "threshold must have a 'trigger' value set");

            Assert.isTrue(TYPE.equals(thresholdConfig.getType()), "threshold for ds-name '" + thresholdConfig.getDatasourceExpression() + "' has type of '" + thresholdConfig.getType() + "', but this evaluator only supports thresholds with a 'type' value of '" + TYPE + "'");

            Assert.isTrue(!Double.isNaN(thresholdConfig.getValue()), "threshold must have a 'value' value that is a number");
            Assert.isTrue(thresholdConfig.getValue() != Double.POSITIVE_INFINITY && thresholdConfig.getValue() != Double.NEGATIVE_INFINITY, "threshold must have a 'value' value that is not positive or negative infinity");
            Assert.isTrue(thresholdConfig.getValue() != 1.0, "threshold must not be unity (1.0)");

            m_thresholdConfig = thresholdConfig;
            setMultiplier(thresholdConfig.getValue());
        }

        @Override
        public BaseThresholdDefConfigWrapper getThresholdConfig() {
            return m_thresholdConfig;
        }

        @Override
        public Status evaluateAfterFetch(double dsValue) {
        	//Fix for Bug 2275 so we handle negative numbers
        	//It will not handle values which cross the 0 boundary (from - to +, or v.v.) properly, but
        	// after some discussion, we can't come up with a sensible scenario when that would actually happen.
        	// If such a scenario eventuates, reconsider
        	dsValue=Math.abs(dsValue);
            if (getLastSample() != 0.0) {
                double threshold = getMultiplier() * getLastSample();

                if (getMultiplier() < 1.0) {
                    if (dsValue <= threshold) {
                        setPreviousTriggeringSample(getLastSample());
                        setLastSample(dsValue);
                        return Status.TRIGGERED;
                    }
                } else {
                    if (dsValue >= threshold) {
                        setPreviousTriggeringSample(getLastSample());
                        setLastSample(dsValue);
                        return Status.TRIGGERED;
                    }
                }

                setLastSample(dsValue);
            }

            setLastSample(dsValue);
            return Status.NO_CHANGE;
        }

        private Double getLastSample() {
            return state.m_lastSample;
        }

        private void setLastSample(double lastSample) {
            if (state.m_lastSample != lastSample) {
                state.m_lastSample = lastSample;
                markDirty();
            }
        }

        @Override
        public Event getEventForState(Status status, Date date, double dsValue, CollectionResourceWrapper resource) {
            if (status == Status.TRIGGERED) {
                final String uei=getThresholdConfig().getTriggeredUEI().orElse(EventConstants.RELATIVE_CHANGE_THRESHOLD_EVENT_UEI);
                return createBasicEvent(uei, date, dsValue, resource);
            } else {
                return null;
            }
        }
        
        private Event createBasicEvent(String uei, Date date, double dsValue, CollectionResourceWrapper resource) {
            Map<String,String> params = new HashMap<String,String>();
            params.put("previousValue", formatValue(getPreviousTriggeringSample()));
            params.put("multiplier", Double.toString(getThresholdConfig().getValue()));
            // params.put("trigger", Integer.toString(getThresholdConfig().getTrigger()));
            // params.put("rearm", Double.toString(getThresholdConfig().getRearm()));
            return createBasicEvent(uei, date, dsValue, resource, params);
        }

        private double getPreviousTriggeringSample() {
            return state.m_previousTriggeringSample;
        }
        
        private void setPreviousTriggeringSample(double previousTriggeringSample) {
            if (state.m_previousTriggeringSample != previousTriggeringSample) {
                state.m_previousTriggeringSample = previousTriggeringSample;
                markDirty();
            }
        }

        private double getMultiplier() {
            return state.m_multiplier;
        }

        private void setMultiplier(double multiplier) {
            if (state.m_multiplier != multiplier) {
                state.m_multiplier = multiplier;
                markDirty();
            }
        }

        @Override
        public ThresholdEvaluatorState getCleanClone() {
            return new ThresholdEvaluatorStateRelativeChange(m_thresholdConfig, getThresholdingSession());
        }

        // FIXME This must be implemented correctly
        @Override
        public boolean isTriggered() {
            return false;
        }
        
        // FIXME This must be implemented correctly
        @Override
        public void clearStateBeforePersist() {
        }

    }

}
