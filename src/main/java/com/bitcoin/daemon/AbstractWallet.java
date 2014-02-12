package com.bitcoin.daemon;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface AbstractWallet {
    public BigDecimal summaryConfirmedBalance(final Set<Object> addresses) throws Exception;
    public BigDecimal summaryConfirmedBalance() throws Exception;
    public List<Address.Transaction> getTransactions(final Set<Object> addresses) throws Exception;
    public List<Address.Transaction> getTransactions() throws Exception;
    public void loadTransactions(int maxCount) throws Exception;
}
