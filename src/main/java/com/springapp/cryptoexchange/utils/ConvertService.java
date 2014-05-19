package com.springapp.cryptoexchange.utils;

import com.springapp.cryptoexchange.database.model.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.Criteria;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public interface ConvertService {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketHistory implements Serializable {
        Order.Type type;
        BigDecimal price;
        BigDecimal amount;
        Date time;
        public MarketHistory(Order order) {
            this(order.getType(), order.getPrice(), order.getCompletedAmount(), order.getCloseDate());
        }
    }

    @Data @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class Depth implements Serializable {
        @Data
        @FieldDefaults(level = AccessLevel.PRIVATE)
        public static class Entry implements Serializable, Comparable<Entry> {
            BigDecimal price;
            BigDecimal amount = BigDecimal.ZERO;
            public int compareTo(Entry entry) {
                return getPrice().compareTo(entry.getPrice());
            }
            protected void addOrder(Order order) {
                setPrice(order.getPrice());
                setAmount(getAmount().add(order.getRemainingAmount()));
            }
        }
        List<Entry> sellOrders;
        List<Entry> buyOrders;
    }

    @Value
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AccountBalance implements Serializable  {
        Currency currency;
        BigDecimal balance;
        String address;
    }

    public Depth createDepth(TradingPair tradingPair, int depthSize) throws Exception;
    public List<MarketHistory> createHistory(Criteria criteria) throws Exception;
    public List<AccountBalance> createAccountBalanceInfo(Account account) throws Exception;
    public Object[][] createHighChartsOHLCData(List<Candle> candleList) throws Exception;
}
