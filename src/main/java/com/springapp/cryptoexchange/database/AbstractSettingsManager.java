package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface AbstractSettingsManager {
    public BigDecimal getFeePercent();
    public BigDecimal getWithdrawFeePercent();
    public List<TradingPair> getTradingPairs();
    public List<Currency> getCurrencyList();
    public void init();
    public void setTestingMode(boolean testingMode);
    public boolean getTestingMode();
}
