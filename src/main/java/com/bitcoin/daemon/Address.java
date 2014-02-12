package com.bitcoin.daemon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Address {
    @Data
    private static class TransactionDetails {
        protected String account;
        protected String address;
        protected BigDecimal amount = BigDecimal.ZERO;
        protected BigDecimal fee = BigDecimal.ZERO;
        protected String category; // send/receive
    }

    @Data
    @EqualsAndHashCode(exclude = {"details", "confirmations"}, callSuper = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transaction extends TransactionDetails {
        protected long time;
        protected String txid;
        protected int confirmations;
        protected List<TransactionDetails> details;

        public boolean isConfirmed() {
            return confirmations >= Settings.REQUIRED_CONFIRMATIONS;
        }
    }
    private final Map<Integer, Transaction> transactionList = new ConcurrentHashMap<>();

    public void addTransaction(Transaction transaction) {
        transactionList.put(transaction.hashCode(), transaction);
    }

    private @NonNull String address;
}