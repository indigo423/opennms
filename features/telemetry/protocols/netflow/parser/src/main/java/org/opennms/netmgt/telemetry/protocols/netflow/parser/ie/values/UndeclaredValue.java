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
package org.opennms.netmgt.telemetry.protocols.netflow.parser.ie.values;

import static org.opennms.netmgt.telemetry.listeners.utils.BufferUtils.bytes;

import java.util.Objects;
import java.util.Optional;

import org.opennms.netmgt.telemetry.protocols.netflow.parser.ie.InformationElement;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.ie.Value;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.session.Session;

import com.google.common.base.MoreObjects;

import io.netty.buffer.ByteBuf;

public class UndeclaredValue extends Value<byte[]> {
    public final byte[] value;

    public UndeclaredValue(final Optional<Long> enterpriseNumber,
                           final int informationElementId,
                           final byte[] value) {
        super(nameFor(enterpriseNumber, informationElementId), Optional.empty());
        this.value = Objects.requireNonNull(value);
    }

    public UndeclaredValue(final int informationElementId,
                           final byte[] value) {
        this(Optional.empty(), informationElementId, value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", getName())
                .add("data", value)
                .toString();
    }

    @Override
    public byte[] getValue() {
        return this.value;
    }

    @Override
    public void visit(final Visitor visitor) {
        visitor.accept(this);
    }

    public static InformationElement parser(final int informationElementId) {
        return parser(Optional.empty(), informationElementId);
    }

    public static InformationElement parser(final Optional<Long> enterpriseNumber,
                                            final int informationElementId) {
        return new InformationElement() {
            @Override
            public Value<?> parse(final Session.Resolver resolver, final ByteBuf buffer) {
                return new UndeclaredValue(enterpriseNumber, informationElementId, bytes(buffer, buffer.readableBytes()));
            }

            @Override
            public String getName() {
                return nameFor(enterpriseNumber, informationElementId);
            }

            @Override
            public int getMinimumFieldLength() {
                return 0;
            }

            @Override
            public int getMaximumFieldLength() {
                return 0xFFFF;
            }
        };
    }

    public static String nameFor(final Optional<Long> enterpriseNumber,
                                 final int informationElementId) {
        return enterpriseNumber.map(en -> Long.toString(en) + ':').orElse("") + Integer.toString(informationElementId);
    }
}
