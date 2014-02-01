package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AbstractDaemonManager;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.database.model.Currency;
import lombok.Data;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;

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

    @Data
    public static class AccountBalanceInfo {
        @Value
        public static class AccountBalance {
            private final Currency currency;
            private final BigDecimal balance;
            private final String address;
        }
        private final List<AccountBalance> accountBalances = new ArrayList<>();
        public void add(Currency currency, BigDecimal balance, Address address) {
            accountBalances.add(new AccountBalance(currency, balance, address.getAddress()));
        }
    }
    public Depth createDepth(List<Order> buyOrders, List<Order> sellOrders) throws Exception;
    public List<MarketHistory> createHistory(List<Order> orders) throws Exception;
    public AccountBalanceInfo createAccountBalanceInfo(Account account) throws Exception;
}
