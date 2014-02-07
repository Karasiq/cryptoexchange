package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AbstractSettingsManager {
    public List<TradingPair> getTradingPairs();
    public List<Currency> getCurrencyList();
    public TradingPair getTradingPair(long id);
    public Currency getCurrency(long id);
}
