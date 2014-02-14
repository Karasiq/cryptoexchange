package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;

import java.util.List;

public interface MarketManager {
    public static class MarketError extends Exception {
        public MarketError() {
            super();
        }

        public MarketError(String message) {
            super(message);
        }

        public MarketError(String message, Throwable cause) {
            super(message, cause);
        }

        public MarketError(Throwable cause) {
            super(cause);
        }

        protected MarketError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
    public Order executeOrder(Order newOrder) throws Exception;
    public void cancelOrder(Order order) throws Exception;
    public Order getOrder(long orderId);
    public List<Order> getOpenOrders(TradingPair tradingPair, Order.Type orderType, int max); // use ConvertService instead
    public void setTradingPairEnabled(TradingPair tradingPair, boolean enabled);
}
