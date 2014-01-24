package com.springapp.cryptoexchange.database;


import com.springapp.cryptoexchange.database.model.Candle;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface AbstractHistoryManager {
    public List<Candle> getMarketChartData(TradingPair tradingPair, int max);
    public void updateChartData(TradingPair tradingPair, final BigDecimal lastPrice, final BigDecimal amount);
    public List<Order> getMarketHistory(TradingPair tradingPair, int max);
    public void init();
}
