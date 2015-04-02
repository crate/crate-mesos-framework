/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.frameworks.mesos;

import org.junit.Test;

import static org.junit.Assert.*;

public class VersionTest {

    static final Version VERSION_2_0_0 = new Version(20000, false);
    static final Version VERSION_1_0_0 = new Version(10000, false);

    @Test
    public void testAfter() throws Exception {
        assertTrue(VERSION_2_0_0.after(VERSION_1_0_0));
        assertFalse(VERSION_1_0_0.after(VERSION_2_0_0));
    }

    @Test
    public void testBefore() throws Exception {
        assertTrue(VERSION_1_0_0.before(VERSION_2_0_0));
        assertFalse(VERSION_2_0_0.before(VERSION_1_0_0));
    }

    @Test
    public void testNumber() throws Exception {
        assertEquals("0.0.1", new Version(1, false).number());
        assertEquals("0.1.0", new Version(100, false).number());
        assertEquals("0.1.1", new Version(101, false).number());
        assertEquals("1.0.0", new Version(10000, false).number());
        assertEquals("1.0.0", new Version(10000, false).number());
        assertEquals("1.0.1", new Version(10001, false).number());
        assertEquals("1.1.0", new Version(10100, false).number());
        assertEquals("1.1.1", new Version(10101, false).number());
    }

    @Test
    public void testToString() throws Exception {
        assertEquals("1.0.0", new Version(10000, false).toString());
        assertEquals("1.0.0-SNAPSHOT", new Version(10000, true).toString());

    }
}