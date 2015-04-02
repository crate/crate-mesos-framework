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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public final class CrateMessage<T extends Serializable> implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(CrateMessage.class);
    private final Type type;
    private final T data;

    public CrateMessage(Type type, T data) {
        this.type = type;
        this.data = data;
    }

    public Type type() {
        return type;
    }

    public T data() {
        return data;
    }

    public enum Type {
        MESSAGE_MISSING_RESOURCE
    }

    public static <E extends Serializable> CrateMessage<E> fromStream(byte[] value) throws IOException {
        if (value.length == 0) {
            return null;
        }
        ByteArrayInputStream in = new ByteArrayInputStream(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(in)) {
            return (CrateMessage<E>) objectInputStream.readObject();
        } catch (ClassNotFoundException e) {
            LOGGER.error("Could not deserialize CrateMessage:", e);
        }
        return null;
    }

    public byte[] toStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream objOut = new ObjectOutputStream(out)) {
            objOut.writeObject(this);
        } catch (IOException e){
            LOGGER.error("Could not serialize CrateMessage:", e);
        }
        return out.toByteArray();
    }
}
