package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AbstractMarketManager {
    public static class MarketError extends Exception {
        public MarketError(String message) {
            super(String.format("Market error: %s", message));
        }
        public MarketError(Throwable throwable) {
            super(String.format("Market error (%s)", throwable.getLocalizedMessage()), throwable);
        }
    }
    public Order executeOrder(Order newOrder) throws Exception;
    public void cancelOrder(Order order) throws Exception;
    public List<Order> getOpenOrders(TradingPair tradingPair, Order.Type type, int max, boolean ascending); // use ConvertService instead
    public void setTradingPairEnabled(TradingPair tradingPair, boolean enabled);
    public void reloadTradingPairs();
}
