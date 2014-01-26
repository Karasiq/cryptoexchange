package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

@Repository
public interface AbstractConvertService {
    static class PriceInfo {
        public BigDecimal last;
        public BigDecimal high;
        public BigDecimal low;
        public BigDecimal average;
        protected PriceInfo(final TradingPair tradingPair) {
            this.last = tradingPair.getLastPrice();
            this.high = tradingPair.getDayHigh();
            this.low = tradingPair.getDayLow();
            if(this.last == null) {
                this.last = BigDecimal.ZERO;
            }
            if(this.high == null) {
                this.high = BigDecimal.ZERO;
            }
            if(this.low == null) {
                this.low = BigDecimal.ZERO;
            }
            this.average = high.add(low).divide(BigDecimal.valueOf(2), 8, RoundingMode.FLOOR);
        }
    }
    static class MarketHistory {
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
    static class Depth {
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
    public PriceInfo createPriceInfo(TradingPair tradingPair);
    public Depth createDepth(List<Order> buyOrders, List<Order> sellOrders);
    public List<MarketHistory> createHistory(List<Order> orders);
}
