package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.AbstractWallet;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.Daemon;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface DaemonManager {
    AbstractWallet getAccount(Currency currency);
    Set getAddressSet(VirtualWallet virtualWallet);
    List<Address> getAddressList(VirtualWallet wallet);
    String createWalletAddress(VirtualWallet virtualWallet) throws Exception;
    com.bitcoin.daemon.Address.Transaction withdrawFunds(VirtualWallet wallet, String address, BigDecimal amount) throws Exception;
    BigDecimal getCryptoBalance(VirtualWallet virtualWallet) throws Exception;
    Daemon getDaemonSettings(Currency currency);
    void setDaemonSettings(Daemon settings);
    void loadDaemons() throws Exception;
    void loadTransactions() throws Exception;
    public List<com.bitcoin.daemon.Address.Transaction> getWalletTransactions(VirtualWallet virtualWallet) throws Exception;
}
