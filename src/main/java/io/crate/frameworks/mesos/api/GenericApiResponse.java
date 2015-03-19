package io.crate.frameworks.mesos.api;


abstract class GenericAPIResponse {

    private int status = 200;
    private String message = "SUCCESS";

    public int getStatus() {
        return this.status;
    }

    public Object getMessage() {
        return this.message;
    }

}
