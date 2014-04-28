package com.bitcoin.daemon;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface AbstractWallet {
    public Set<?> getAddressSet() throws Exception;
    public BigDecimal summaryConfirmedBalance(final Set<?> addresses) throws Exception;
    public BigDecimal summaryConfirmedBalance() throws Exception;
    public List<Address.Transaction> getTransactions(final Set<?> addresses) throws Exception;
    public List<Address.Transaction> getTransactions() throws Exception;
    public Address.Transaction getTransaction(String transactionId) throws Exception;
    public void loadTransactions(int maxCount) throws Exception;
}
