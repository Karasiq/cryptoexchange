package com.springapp.cryptoexchange.config;

import lombok.Value;
import org.joda.time.DateTimeZone;


public class ServerSettings {
    @Value
    public static class Settings {
        private String hashingSalt;
        private DateTimeZone serverTimeZone;
    }
    public static final Settings serverSettings = new Settings("test", DateTimeZone.UTC);
}
