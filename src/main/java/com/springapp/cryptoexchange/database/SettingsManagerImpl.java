package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class SettingsManagerImpl implements SettingsManager {
    @Autowired
    SessionFactory sessionFactory;

    @Transactional
    @SuppressWarnings("unchecked")
    public List<TradingPair> getTradingPairs() {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(TradingPair.class)
                .list();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Currency> getCurrencyList() {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Currency.class)
                .list();
    }

    @Transactional
    public TradingPair getTradingPair(long id) {
        Session session = sessionFactory.getCurrentSession();
        return (TradingPair) session.get(TradingPair.class, id);
    }

    @Transactional
    public Currency getCurrency(long id) {
        Session session = sessionFactory.getCurrentSession();
        return (Currency) session.get(Currency.class, id);
    }
}
