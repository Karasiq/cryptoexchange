package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.JsonRPC;
import com.springapp.cryptoexchange.database.model.Currency;
import org.springframework.stereotype.Repository;

@Repository
public interface AbstractDaemonManager {
    public JsonRPC getDaemon(Currency currency);
    public AbstractWallet getAccount(Currency currency);
    public void init() throws Exception;
}
