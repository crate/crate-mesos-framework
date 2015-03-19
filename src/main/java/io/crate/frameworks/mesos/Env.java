package io.crate.frameworks.mesos;

import com.google.common.base.Optional;

public class Env {

    public static Optional<String> option(String key) {
        return Optional.fromNullable(System.getenv(key));
    }
}
