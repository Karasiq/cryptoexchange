package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.FreeBalance;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface AbstractFeeManager {
    void submitCollectedFee(FreeBalance.FeeType type, Currency currency, BigDecimal feeAmount) throws Exception;
    BigDecimal getCollectedFee(FreeBalance.FeeType type, Currency currency);
    void withdrawFee(FreeBalance.FeeType type, Currency currency, BigDecimal amount, Object receiverInfo) throws Exception;
    void withdrawFee(Currency currency, BigDecimal amount, Object receiverInfo) throws Exception; // From all available
}
