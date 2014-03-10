package com.springapp.cryptoexchange.webapi;

public final class ApiDefs {
    public static class ApiException extends Exception {
        protected ApiException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }

        public ApiException(String message) {
            super(message);
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }

        public ApiException(Throwable cause) {
            super(cause);
        }
    }
}
