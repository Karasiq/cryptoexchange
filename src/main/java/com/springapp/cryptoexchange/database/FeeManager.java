package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.FreeBalance;

import java.math.BigDecimal;

public interface FeeManager {
    void submitCollectedFee(FreeBalance.FeeType type, Currency currency, BigDecimal feeAmount) throws Exception;
    BigDecimal getCollectedFee(Currency currency);
    Object withdrawFee(Currency currency, BigDecimal amount, Object receiverInfo) throws Exception;
}
