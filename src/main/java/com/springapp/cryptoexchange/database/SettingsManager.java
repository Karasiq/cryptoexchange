package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class SettingsManager {
    private Log log = LogFactory.getLog(SettingsManager.class);
    private @Getter final BigDecimal feePercent = new BigDecimal(0.2);
    private @Getter final BigDecimal withdrawFeePercent = new BigDecimal(3);
    private @Getter final Map<Currency, CryptoCoinWallet.Account> walletDefaultAccounts = new HashMap<>();
    private @Getter List<Currency> currencyList = null;
    private @Getter List<TradingPair> tradingPairs = null;

    private final Map<Currency, JsonRPC> daemonMap = new HashMap<>();

    @Autowired
    SessionFactory sessionFactory;

    private void loadTransactions(final int max) {
        synchronized (daemonMap) {
            for(Currency currency : currencyList) {
                final JsonRPC jsonRPC = daemonMap.get(currency);
                final CryptoCoinWallet.Account account = walletDefaultAccounts.get(currency);
                if(jsonRPC != null && account != null) {
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                account.loadAddresses(jsonRPC);
                                account.loadTransactions(jsonRPC, max);
                            } catch (Exception e) {
                                log.error(e);
                                e.printStackTrace();
                            }
                        }
                    });
                    thread.start();
                }
            }
        }
    }

    private void initDaemons() {
        synchronized (daemonMap) {
            for(Currency currency : currencyList) {
                if(!daemonMap.containsKey(currency)) {
                    daemonMap.put(currency, new JsonRPC(currency.getDaemonHost(), currency.getDaemonPort(), currency.getDaemonLogin(), currency.getDaemonPassword()));
                }
                if(!walletDefaultAccounts.containsKey(currency)) {
                    walletDefaultAccounts.put(currency, CryptoCoinWallet.getDefaultAccount());
                }
            }
        }
    }

    public JsonRPC getDaemon(Currency currency) {
        synchronized (daemonMap) {
            return daemonMap.get(currency);
        }
    }

    public CryptoCoinWallet.Account getAccount(Currency currency) {
        synchronized (daemonMap) {
            return walletDefaultAccounts.get(currency);
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void loadCurrencies() {
        synchronized (daemonMap) {
            Session session = sessionFactory.getCurrentSession();
            currencyList = session.createCriteria(Currency.class).list();
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void loadTradingPairs() {
        synchronized (daemonMap) {
            Session session = sessionFactory.getCurrentSession();
            tradingPairs = session.createCriteria(TradingPair.class)
                    .add(Restrictions.eq("enabled", true))
                    .list();
        }
    }

    public void init() {
        loadTradingPairs();
        loadCurrencies();
        initDaemons();
        loadTransactions(20000);
    }
}
