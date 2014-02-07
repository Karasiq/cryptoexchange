package com.bitcoin.daemon;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Data
@NoArgsConstructor
public class TestingWallet implements AbstractWallet {
    public TestingWallet(String accountName) {
        // nothing
    }
    private @Setter BigDecimal testingAmount = BigDecimal.TEN;

    public BigDecimal summaryConfirmedBalance(final Set<Object> addresses) throws Exception {
        return testingAmount;
    }
    public BigDecimal summaryConfirmedBalance() throws Exception {
        return testingAmount;
    }
    public void resetUnconfirmedBalance() throws Exception {
        // nothing
    }
    public void loadTransactions(int maxCount) throws Exception {
        // nothing
    }
}
