package com.springapp.cryptoexchange.utils;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Candle;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.Order;
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
    public static class MarketHistory implements Serializable {
        Order.Type type;
        BigDecimal price;
        BigDecimal amount;
        Date time;
        public MarketHistory(Order order) {
            type = order.getType();
            price = order.getPrice();
            amount = order.getCompletedAmount();
            time = order.getCloseDate();
        }
    }

    @Data @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    public static class Depth implements Serializable {
        @EqualsAndHashCode
        static class Entry implements Serializable, Comparable<Entry> {
            protected void addOrder(Order order) {
                price = order.getPrice();
                amount = amount.add(order.getRemainingAmount());
            }
            public BigDecimal price;
            public BigDecimal amount = BigDecimal.ZERO;
            public int compareTo(Entry entry) {
                return price.compareTo(entry.price);
            }
        }
        List<Entry> sellOrders = new ArrayList<>();
        List<Entry> buyOrders = new ArrayList<>();
    }

    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class AccountBalanceInfo implements Serializable  {
        @Value
        public static class AccountBalance implements Serializable  {
            Currency currency;
            BigDecimal balance;
            String address;
        }
        @Getter List<AccountBalance> accountBalances = new ArrayList<>();
        public void add(Currency currency, BigDecimal balance, String address) {
            synchronized (accountBalances) {
                accountBalances.add(new AccountBalance(currency, balance, address));
            }
        }
    }

    public Depth createDepth(Criteria buyOrders, Criteria sellOrders, int depthSize) throws Exception;
    public List<MarketHistory> createHistory(Criteria orders) throws Exception;
    public AccountBalanceInfo createAccountBalanceInfo(Account account) throws Exception;
    public Object[][] createHighChartsOHLCData(List<Candle> candleList) throws Exception;
}
