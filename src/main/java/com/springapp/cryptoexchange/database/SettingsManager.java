package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;

import java.util.List;

public interface SettingsManager {
    public List<TradingPair> getTradingPairs();
    public List<Currency> getCurrencyList();
    public TradingPair getTradingPair(long id);
    public Currency getCurrency(long id);
    public void addCurrency(Currency currency) throws Exception;
    public void addTradingPair(TradingPair tradingPair) throws Exception;
    public void removeTradingPair(TradingPair tradingPair) throws Exception;
}
