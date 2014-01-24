package com.bitcoin.daemon;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.math.BigDecimal;
import java.util.Set;

@Data
@NoArgsConstructor
public class TestingWallet implements AbstractWallet {
    public TestingWallet(String accountName) {
        // nothing
    }
    private @Setter BigDecimal testingAmount = BigDecimal.TEN;

    public BigDecimal summaryConfirmedBalance(final Set<String> addresses) throws Exception {
        return testingAmount;
    }
    public BigDecimal summaryConfirmedBalance() throws Exception {
        return testingAmount;
    }
    public void resetUnconfirmedBalance() throws Exception {
        // nothing
    }
    public void loadAddresses(final JsonRPC jsonRPC) throws Exception {
        // nothing
    }
    public void loadTransactions(final JsonRPC jsonRPC, int maxCount) throws Exception {
        // nothing
    }
    public Address generateNewAddress(final JsonRPC jsonRPC) throws Exception {
        return new Address(String.format("testing-address-%d", System.currentTimeMillis()));
    }
    public Address.Transaction sendToAddress(final JsonRPC jsonRPC, final String address, final BigDecimal amount) throws Exception {
        throw new NotImplementedException();
    }
}
