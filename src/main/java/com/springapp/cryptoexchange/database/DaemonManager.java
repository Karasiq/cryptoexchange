package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import com.bitcoin.daemon.TestingWallet;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
@CommonsLog
public class DaemonManager implements AbstractDaemonManager {
    @RequiredArgsConstructor
    private static class DaemonInfo {
        boolean enabled = true;
        @NonNull AbstractWallet wallet;
    }
    private final Map<Currency, DaemonInfo> daemonMap = new ConcurrentHashMap<>();

    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Scheduled(fixedDelay = 1000 * 60 * 5) // Every 5m
    private synchronized void loadTransactions(final int max) throws Exception {
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) {
            final DaemonInfo daemonInfo = daemonMap.get(currency);
            if(daemonInfo.enabled && daemonInfo.wallet != null) {
                daemonInfo.wallet.loadTransactions(max);
            }
        }
    }

    private synchronized void initDaemons() {
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) {
            if(!daemonMap.containsKey(currency)) {
                JsonRPC daemon = new JsonRPC(currency.getDaemonHost(), currency.getDaemonPort(), currency.getDaemonLogin(), currency.getDaemonPassword());
                AbstractWallet wallet = settingsManager.isTestingMode() ? new TestingWallet() : CryptoCoinWallet.getDefaultAccount(daemon);
                daemonMap.put(currency, new DaemonInfo(wallet));
            }
        }
    }

    public AbstractWallet getAccount(Currency currency) {
        DaemonInfo daemonInfo = daemonMap.get(currency);
        assert daemonInfo.enabled;
        return daemonInfo.wallet;
    }

    @Transactional
    public String createWalletAddress(@NonNull VirtualWallet virtualWallet, @NonNull CryptoCoinWallet.Account account) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        session.saveOrUpdate(virtualWallet);
        com.bitcoin.daemon.Address newAddress = account.generateNewAddress();
        Address address = virtualWallet.addAddress(newAddress.getAddress());
        session.saveOrUpdate(address);
        return newAddress.getAddress();
    }

    public void init() throws Exception {
        initDaemons();
        loadTransactions(20000);
    }
}
