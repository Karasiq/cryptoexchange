package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.Address;
import com.springapp.cryptoexchange.database.model.VirtualWallet;

import java.math.BigDecimal;

public interface TransactionLogManager {
    void addCryptoTransaction(Address.Transaction transaction);
    void addInternalTransaction(VirtualWallet source, VirtualWallet dest, BigDecimal amount);
}
