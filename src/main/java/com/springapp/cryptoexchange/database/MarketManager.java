package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.hibernate.Criteria;

public interface MarketManager {
    public static class MarketException extends Exception {
        public MarketException() {
            super();
        }

        public MarketException(String message) {
            super(message);
        }

        public MarketException(String message, Throwable cause) {
            super(message, cause);
        }

        public MarketException(Throwable cause) {
            super(cause);
        }

        protected MarketException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
    public Order executeOrder(Order newOrder) throws Exception;
    public void cancelOrder(Order order) throws Exception;
    public Order getOrder(long orderId);
    public Criteria getOpenOrders(TradingPair tradingPair, Order.Type orderType);
}
