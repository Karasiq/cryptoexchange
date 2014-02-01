package com.springapp.cryptoexchange.database;


import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Candle;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface AbstractHistoryManager {
    public void updateChartData(TradingPair tradingPair, final BigDecimal lastPrice, final BigDecimal amount);

    public List<Candle> getMarketChartData(TradingPair tradingPair, int max); // use ConvertService instead
    public List<Order> getMarketHistory(TradingPair tradingPair, int max); // use ConvertService instead
    public List<Order> getAccountHistory(Account account, int max);
    public List<Order> getAccountHistoryByPair(TradingPair tradingPair, Account account, int max);
}
