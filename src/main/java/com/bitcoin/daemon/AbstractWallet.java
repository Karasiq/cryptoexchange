package com.bitcoin.daemon;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface AbstractWallet<A, T extends AbstractTransaction> {
    public Set<A> getAddressSet() throws Exception;
    public BigDecimal summaryConfirmedBalance(final Set<A> addresses) throws Exception;
    public BigDecimal summaryConfirmedBalance() throws Exception;
    public BigDecimal getAddressBalance(A address) throws Exception;
    public List<T> getTransactions(final Set<A> addresses) throws Exception;
    public List<T> getTransactions() throws Exception;
    public T getTransaction(String transactionId) throws Exception;
    public void loadTransactions(int maxCount) throws Exception;
    public A generateNewAddress() throws Exception;
    public T sendToAddress(A address, BigDecimal amount) throws Exception;
}
