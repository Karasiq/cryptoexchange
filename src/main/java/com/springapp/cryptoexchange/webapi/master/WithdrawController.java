package com.springapp.cryptoexchange.webapi.master;

import com.bitcoin.daemon.Address;

import java.math.BigDecimal;
import java.security.Principal;

public interface WithdrawController {
    Address.Transaction withdrawCrypto(long currencyId, String address, BigDecimal amount, Principal principal) throws Exception;
}
