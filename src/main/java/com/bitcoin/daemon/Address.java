package com.bitcoin.daemon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Address {
    @Data
    @FieldDefaults(level = AccessLevel.PUBLIC)
    private static class TransactionDetails implements Serializable {
        @JsonIgnore String account;
        String address;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        String category; // send/receive
    }

    @Data
    @EqualsAndHashCode(exclude = {"details"}, callSuper = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class Transaction extends TransactionDetails implements Serializable, Comparable<Transaction> {
        long time;
        String txid;
        transient int confirmations;
        List<TransactionDetails> details;

        public boolean isConfirmed() {
            return confirmations >= Settings.REQUIRED_CONFIRMATIONS;
        }

        @Override
        public int compareTo(Transaction transaction) {
            return Long.compare(time, transaction.time);
        }
    }

    Map<Integer, Transaction> transactionList = new ConcurrentHashMap<>();
    @NonNull String address;

    public void addTransaction(Transaction transaction) {
        transactionList.put(transaction.hashCode(), transaction);
    }
}