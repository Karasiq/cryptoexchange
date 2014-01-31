package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.Cleanup;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
@Transactional
public class SettingsManager implements AbstractSettingsManager {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AbstractMarketManager marketManager;

    private @Getter List<Currency> currencyList = null;
    private @Getter List<TradingPair> tradingPairs = null;
    private @Getter @Setter boolean testingMode = false;


    @CacheEvict(value = "getTradingPairs", allEntries = true)
    @Transactional
    public void addTradingPair(TradingPair newTradingPair) {
        synchronized (tradingPairs) {
            tradingPairs.add(newTradingPair);
            marketManager.reloadTradingPairs();
        }
    }

    @Transactional
    private void addCurrency(Currency newCurrency) {
        synchronized (currencyList) {
            currencyList.add(newCurrency);
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    private void loadCurrencies(Session session) {
        currencyList = session.createCriteria(Currency.class)
                .add(Restrictions.eq("enabled", true)).list();
    }

    @CacheEvict("getTradingPairs")
    @Transactional
    @SuppressWarnings("unchecked")
    public synchronized void loadTradingPairs(Session session) {
        tradingPairs = session.createCriteria(TradingPair.class)
                .add(Restrictions.eq("enabled", true)).list();
    }

    public TradingPair getTradingPair(long id) {
        synchronized (tradingPairs) {
            for(TradingPair tradingPair : tradingPairs) {
                if(tradingPair.getId() == id) {
                    return tradingPair;
                }
            }
        }
        return null;
    }

    public Currency getCurrency(long id) {
        synchronized (currencyList) {
            for(Currency currency : currencyList) {
                if(currency.getId() == id) {
                    return currency;
                }
            }
        }
        return null;
    }

    @PostConstruct
    public synchronized void init() {
        @Cleanup Session session = sessionFactory.openSession();
        loadCurrencies(session);
        loadTradingPairs(session);
        marketManager.reloadTradingPairs();
    }
}
