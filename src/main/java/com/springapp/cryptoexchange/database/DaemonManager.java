package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.AbstractWallet;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.Daemon;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.util.List;

public interface DaemonManager {
    AbstractWallet getAccount(Currency currency);
    List<Address> getAddressList(VirtualWallet wallet);
    String createWalletAddress(VirtualWallet virtualWallet) throws Exception;
    void withdrawFunds(VirtualWallet wallet, String address, BigDecimal amount) throws Exception;
    public Daemon getDaemonSettings(Currency currency);
    public void setDaemonSettings(Daemon settings);
    public void loadDaemons() throws Exception;
    void loadTransactions() throws Exception;
}
