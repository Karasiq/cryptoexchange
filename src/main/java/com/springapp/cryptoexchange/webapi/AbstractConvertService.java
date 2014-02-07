package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.Order;
import lombok.Data;
import lombok.Value;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
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
            amount = order.getCompletedAmount();
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
        public final List<DepthEntry> sellOrders = new ArrayList<>();
        public final List<DepthEntry> buyOrders = new ArrayList<>();
    }

    @Data
    public static class AccountBalanceInfo {
        @Value
        public static class AccountBalance {
            private final Currency currency;
            private final BigDecimal balance;
            private final String address;
        }
        private final List<AccountBalance> accountBalances = new ArrayList<>();
        public void add(Currency currency, BigDecimal balance, String address) {
            accountBalances.add(new AccountBalance(currency, balance, address));
        }
    }
    public Depth createDepth(List<Order> buyOrders, List<Order> sellOrders) throws Exception;
    public List<MarketHistory> createHistory(List<Order> orders) throws Exception;
    public AccountBalanceInfo createAccountBalanceInfo(Account account) throws Exception;
}
