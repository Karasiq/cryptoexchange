package com.bitcoin.daemon;

import java.math.BigDecimal;

final class Settings {
    public static final int REQUIRED_CONFIRMATIONS = 6;
    public static final String DEFAULT_ACCOUNT = "";
    public static final String BITCOIN_ADDRESS_REGEXP = "^[0-9A-z]{27,34}$";
    public static final String BITCOIN_TXID_REGEXP = "^[0-9a-f]*$";
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.00000001");
}
