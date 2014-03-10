package com.springapp.cryptoexchange.database;


import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Candle;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;

import java.math.BigDecimal;
import java.util.List;

public interface HistoryManager {
    void updateMarketInfo(TradingPair tradingPair, final BigDecimal price, final BigDecimal amount);
    List<Candle> getMarketChartData(TradingPair tradingPair, int max); // use ConvertService instead
    List<Order> getMarketHistory(TradingPair tradingPair, int max); // use ConvertService instead
    List<Order> getAccountHistory(Account account, int max);
    List<Order> getAccountHistoryByPair(TradingPair tradingPair, Account account, int max);
}
