package com.bitcoin.daemon;

import java.math.BigDecimal;
import java.util.Set;

public interface AbstractWallet {
    public BigDecimal summaryConfirmedBalance(final Set<String> addresses) throws Exception;
    public BigDecimal summaryConfirmedBalance() throws Exception;
    public void resetUnconfirmedBalance() throws Exception;
    public void loadAddresses(final JsonRPC jsonRPC) throws Exception;
    public void loadTransactions(final JsonRPC jsonRPC, int maxCount) throws Exception;
    public Address generateNewAddress(final JsonRPC jsonRPC) throws Exception;
    public Address.Transaction sendToAddress(final JsonRPC jsonRPC, final String address, final BigDecimal amount) throws Exception;
}
