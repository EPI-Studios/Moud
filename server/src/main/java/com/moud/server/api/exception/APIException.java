package com.moud.server.api.exception;

public class APIException extends RuntimeException {
    private final String errorCode;
    private final Object[] params;

    public APIException(String message) {
        super(message);
        this.errorCode = "GENERAL_ERROR";
        this.params = new Object[0];
    }

    public APIException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "GENERAL_ERROR";
        this.params = new Object[0];
    }

    public APIException(String errorCode, String message, Object... params) {
        super(message);
        this.errorCode = errorCode;
        this.params = params;
    }

    public APIException(String errorCode, String message, Throwable cause, Object... params) {
        super(message, cause);
        this.errorCode = errorCode;
        this.params = params;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getParams() {
        return params;
    }
}