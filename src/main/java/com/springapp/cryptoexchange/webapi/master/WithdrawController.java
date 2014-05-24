package com.springapp.cryptoexchange.webapi.master;

import com.bitcoin.daemon.AbstractTransaction;

import java.math.BigDecimal;
import java.security.Principal;

public interface WithdrawController {
    AbstractTransaction withdrawCrypto(long currencyId, String address, BigDecimal amount, Principal principal) throws Exception;
}
