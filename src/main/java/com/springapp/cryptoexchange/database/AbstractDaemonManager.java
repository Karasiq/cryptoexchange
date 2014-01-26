package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import org.springframework.stereotype.Repository;

@Repository
public interface AbstractDaemonManager {
    public AbstractWallet getAccount(Currency currency);
    public String createWalletAddress(VirtualWallet virtualWallet, CryptoCoinWallet.Account account) throws Exception;
    public void init() throws Exception;
}
