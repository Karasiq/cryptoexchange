package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class SettingsManager implements AbstractSettingsManager {
    @Autowired
    SessionFactory sessionFactory;

    private @Getter List<Currency> currencyList = null;
    private @Getter List<TradingPair> tradingPairs = null;
    private @Getter @Setter boolean testingMode = false;


    @Transactional
    @SuppressWarnings("unchecked")
    public synchronized void loadCurrencies() {
        Session session = sessionFactory.getCurrentSession();
        currencyList = session.createCriteria(Currency.class)
                .add(Restrictions.eq("enabled", true)).list();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public synchronized void loadTradingPairs() {
        Session session = sessionFactory.getCurrentSession();
        tradingPairs = session.createCriteria(TradingPair.class)
                .add(Restrictions.eq("enabled", true)).list();
    }

    public TradingPair getTradingPair(long id) {
        for(TradingPair tradingPair : tradingPairs) {
            if(tradingPair.getId() == id) {
                return tradingPair;
            }
        }
        return null;
    }

    public Currency getCurrency(long id) {
        for(Currency currency : currencyList) {
            if(currency.getId() == id) {
                return currency;
            }
        }
        return null;
    }

    public synchronized void init() {
        loadCurrencies();
        loadTradingPairs();
    }
}
