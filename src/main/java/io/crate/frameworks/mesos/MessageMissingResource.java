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
