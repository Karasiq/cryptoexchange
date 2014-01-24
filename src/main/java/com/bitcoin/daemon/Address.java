package com.bitcoin.daemon;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

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
    @EqualsAndHashCode(exclude = {"details", "confirmations"}, callSuper = false)
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
    private final HashMap<Integer, Transaction> transactionList = new HashMap<>();
    public void addTransaction(Transaction transaction) {
        synchronized (transactionList) {
            int txHashCode = transaction.hashCode();
            if(!transactionList.containsKey(txHashCode)) {
                BigDecimal amount = transaction.getAmount(), fee = transaction.getFee();
                if(transaction.isConfirmed()) {
                    transactionList.put(txHashCode, transaction);
                    confirmedBalance = confirmedBalance.add(amount).add(fee);
                } else {
                    if(amount.compareTo(BigDecimal.ZERO) < 0) { // Withdraw
                        unconfirmedWithdraw = unconfirmedWithdraw.add(amount).add(fee);
                    } else {
                        unconfirmedBalance = unconfirmedBalance.add(amount).add(fee);
                    }
                }
            }
        }
    }
    public void resetUnconfirmed() {
        setUnconfirmedBalance(BigDecimal.ZERO);
        setUnconfirmedWithdraw(BigDecimal.ZERO);
    }

    private BigDecimal confirmedBalance = BigDecimal.ZERO;
    private BigDecimal unconfirmedBalance = BigDecimal.ZERO;
    private BigDecimal unconfirmedWithdraw = BigDecimal.ZERO;
    private @NonNull
    String address;
}