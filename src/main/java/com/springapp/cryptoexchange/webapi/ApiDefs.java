package com.springapp.cryptoexchange.webapi;

import lombok.Value;

public final class ApiDefs {
    @Value
    public static class ApiStatus<T> {
        private final boolean success;
        private final String error;
        private final T data;
    }
}
