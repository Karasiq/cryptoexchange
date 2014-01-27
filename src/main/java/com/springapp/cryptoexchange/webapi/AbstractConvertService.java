package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.Data;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Repository
public interface AbstractConvertService {
    @Data
    public static class MarketHistory {
        public Order.Type type;
        public BigDecimal price;
        public BigDecimal amount;
        public Date time;
        protected MarketHistory(Order order) {
            type = order.getType();
            price = order.getPrice();
            amount = order.getAmount();
            time = order.getCloseDate();
        }
    }

    @Data
    public static class Depth {
        static class DepthEntry implements Comparable<DepthEntry> {
            protected void addOrder(Order order) {
                price = order.getPrice();
                amount = amount.add(order.getRemainingAmount());
            }
            public BigDecimal price;
            public BigDecimal amount = BigDecimal.ZERO;
            public int compareTo(DepthEntry entry) {
                return price.compareTo(entry.price);
            }
        }
        public List<DepthEntry> sellOrders;
        public List<DepthEntry> buyOrders;
    }
    public Depth getMarketDepth(TradingPair tradingPair);
    public List<MarketHistory> getMarketHistory(TradingPair tradingPair);
    public void clearDepthCache();
    public void clearHistoryCache();
}
