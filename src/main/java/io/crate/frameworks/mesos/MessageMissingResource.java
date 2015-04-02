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

package io.crate.frameworks.mesos;

import java.io.Serializable;

public class MessageMissingResource implements Serializable {
    private Reason reason;

    public final static MessageMissingResource MISSING_DATA_PATH = new MessageMissingResource(Reason.MISSING_DATA_PATH);
    public final static MessageMissingResource MISSING_BLOB_PATH = new MessageMissingResource(Reason.MISSING_BLOB_PATH);

    private final static String MISSING_DATA_PATH_VALUE = "MISSING_DATA_PATH";
    private final static String MISSING_BLOB_PATH_VALUE = "MISSING_BLOB_PATH";

    private static final long serialVersionUID = 1L;

    public enum Reason {

        MISSING_DATA_PATH(MISSING_DATA_PATH_VALUE),
        MISSING_BLOB_PATH(MISSING_BLOB_PATH_VALUE);

        String name;

        Reason(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public MessageMissingResource(Reason reason) {
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
