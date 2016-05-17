/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.frameworks.mesos.integration;

import com.mashape.unirest.http.exceptions.UnirestException;
import io.crate.frameworks.mesos.CrateInstances;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ScaleIntegrationTest extends BaseIntegrationTest {

    @Test
    public void testScaleWithMinimumMasterNodes() throws UnirestException {
        // starting with 0 instances
        assertThat(crateNodesCount(), is(0));
        // then scale to 3 .. 2 .. 1 instances
        for (int i = 3; i > 0; i--) {
            scaleCrate(i);
            assertThat(CrateInstances.calculateQuorum(i), is(getMinMasterNodes()));
            assertThat(crateNodesCount(), is(i));
        }

    }

    private int getMinMasterNodes() throws UnirestException {
        JSONArray rows = execute("SELECT settings['discovery']['zen']['minimum_master_nodes'] FROM sys.cluster")
                .getBody().getObject().getJSONArray("rows");
        return rows.getJSONArray(0).getInt(0);
    }

    @After
    public void tearDown() throws UnirestException {
        shutdown();
    }

}
