package com.springapp.cryptoexchange.database;


import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Candle;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.hibernate.Criteria;

import java.math.BigDecimal;
import java.util.List;

public interface HistoryManager {
    void updateMarketInfo(TradingPair tradingPair, final BigDecimal price, final BigDecimal amount);
    Criteria getMarketChartData(TradingPair tradingPair);
    Criteria getMarketHistory(TradingPair tradingPair);
    Criteria getAccountHistory(Account account);
    Criteria getAccountHistoryByPair(TradingPair tradingPair, Account account);
}
