package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface AbstractFeeManager {
    void submitCollectedFee(Currency currency, BigDecimal feeAmount) throws Exception;
    BigDecimal getCollectedFee(Currency currency);
    void withdrawFee(Currency currency, BigDecimal amount, Object receiverInfo) throws Exception;
}
