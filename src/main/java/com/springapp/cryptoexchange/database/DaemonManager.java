package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import com.bitcoin.daemon.TestingWallet;
import com.springapp.cryptoexchange.database.model.Currency;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class DaemonManager implements AbstractDaemonManager {
    @RequiredArgsConstructor
    private class DaemonInfo {
        boolean enabled = true;
        @NonNull JsonRPC daemon;
        @NonNull AbstractWallet wallet;
    }
    private Log log = LogFactory.getLog(DaemonManager.class);
    private final Map<Currency, DaemonInfo> daemonMap = new HashMap<>();

    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AbstractSettingsManager settingsManager;

    private synchronized void loadTransactions(final int max) throws Exception {
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) {
            final DaemonInfo daemonInfo = daemonMap.get(currency);
            if(daemonInfo.enabled && daemonInfo.daemon != null && daemonInfo.wallet != null) {
                daemonInfo.wallet.loadAddresses(daemonInfo.daemon);
                daemonInfo.wallet.loadTransactions(daemonInfo.daemon, max);
            }
        }
    }

    private synchronized void initDaemons() {
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) {
            if(!daemonMap.containsKey(currency)) {
                daemonMap.put(currency, new DaemonInfo(new JsonRPC(currency.getDaemonHost(), currency.getDaemonPort(), currency.getDaemonLogin(), currency.getDaemonPassword()), settingsManager.getTestingMode() ? new TestingWallet() : CryptoCoinWallet.getDefaultAccount()));
            }
        }
    }

    public synchronized JsonRPC getDaemon(Currency currency) {
        DaemonInfo daemonInfo = daemonMap.get(currency);
        assert daemonInfo.enabled;
        return daemonInfo.daemon;
    }

    public synchronized AbstractWallet getAccount(Currency currency) {
        DaemonInfo daemonInfo = daemonMap.get(currency);
        assert daemonInfo.enabled;
        return daemonInfo.wallet;
    }

    public void init() throws Exception {
        initDaemons();
        loadTransactions(20000);
    }
}
