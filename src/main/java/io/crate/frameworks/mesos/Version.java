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

import org.elasticsearch.monitor.jvm.JvmInfo;

public class Version {

    /**
     * The logic for the Version ID is: XXYYZZ, where XX is major version,
     * YY is minor version, and ZZ is the revision.
     */

    public static final boolean SNAPSHOT = true;
    public static final Version CURRENT = new Version(200, SNAPSHOT);

    public final int id;
    public final byte major;
    public final byte minor;
    public final byte revision;
    public final boolean snapshot;

    Version(int id, boolean snapshot) {
        this.id = id;
        this.major = (byte) ((id / 10000) % 100);
        this.minor = (byte) ((id / 100) % 100);
        this.revision = (byte) (id % 100);
        this.snapshot = snapshot;
    }

    public boolean snapshot() {
        return snapshot;
    }

    public boolean after(Version version) {
        return version.id < id;
    }

    public boolean before(Version version) {
        return version.id > id;
    }

    /**
     * Just the version number (without -SNAPSHOT if snapshot).
     */
    public String number() {
        StringBuilder sb = new StringBuilder()
                .append(major).append('.')
                .append(minor).append('.')
                .append(revision);
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("Version: " + Version.CURRENT + ", JVM: " + JvmInfo.jvmInfo().version() );
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(number());
        if (snapshot) {
            sb.append("-SNAPSHOT");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Version version = (Version) o;

        if (id != version.id) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id;
    }

}

