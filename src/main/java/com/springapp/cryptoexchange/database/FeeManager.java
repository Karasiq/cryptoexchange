package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.FreeBalance;

import java.math.BigDecimal;
import java.util.List;

public interface FeeManager {
    void submitCollectedFee(FreeBalance.FeeType type, Currency currency, BigDecimal feeAmount) throws Exception;
    BigDecimal getCollectedFee(Currency currency);
    Object withdrawFee(Currency currency, BigDecimal amount, Object receiverInfo) throws Exception;
    List<FreeBalance> getFreeBalances();
    FreeBalance getFreeBalance(Currency currency);
}
