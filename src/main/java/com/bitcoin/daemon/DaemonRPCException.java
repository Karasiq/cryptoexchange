package com.bitcoin.daemon;

public class DaemonRpcException extends RuntimeException {
    public DaemonRpcException() {
        super();
    }

    public DaemonRpcException(String message) {
        super(message);
    }

    public DaemonRpcException(String message, Throwable cause) {
        super(message, cause);
    }

    public DaemonRpcException(Throwable cause) {
        super(cause);
    }

    protected DaemonRpcException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}