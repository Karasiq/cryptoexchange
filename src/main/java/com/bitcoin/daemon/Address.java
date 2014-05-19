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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "address")
public class Address { // Transaction/balance storage
    @Data
    @FieldDefaults(level = AccessLevel.PUBLIC)
    @EqualsAndHashCode(exclude = "account")
    private static class TransactionDetails implements Serializable {
        @JsonIgnore String account;
        String address;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        String category; // send/receive
    }

    @Data
    @EqualsAndHashCode(of = "txid", callSuper = false)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class Transaction extends TransactionDetails implements Serializable, AbstractTransaction {
        Date time;
        String txid;
        transient int confirmations;
        List<TransactionDetails> details;

        public boolean isConfirmed() {
            return confirmations >= Settings.REQUIRED_CONFIRMATIONS;
        }
    }

    BigDecimal receivedByAddress = BigDecimal.ZERO; // Cached
    final Set<Transaction> transactionSet = new HashSet<>();
    final @NonNull String address;

    public void addTransaction(Transaction transaction) {
        transactionSet.add(transaction);
    }
}