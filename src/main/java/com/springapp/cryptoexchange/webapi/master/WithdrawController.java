package com.springapp.cryptoexchange.webapi.master;

import com.springapp.cryptoexchange.webapi.ApiDefs;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.security.Principal;

public interface WithdrawController {
    ApiDefs.ApiStatus withdrawCrypto(long currencyId, String address, BigDecimal amount, Principal principal);
}
