package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class SettingsManagerImpl implements SettingsManager {
    @Autowired
    SessionFactory sessionFactory;

    @Override
    @SuppressWarnings("unchecked")
    public List<TradingPair> getTradingPairs() {
        return sessionFactory.getCurrentSession().createCriteria(TradingPair.class)
                .list();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Currency> getCurrencyList() {
        return sessionFactory.getCurrentSession().createCriteria(Currency.class)
                .list();
    }

    @Transactional
    @Override
    public TradingPair getTradingPair(long id) {
        return (TradingPair) sessionFactory.getCurrentSession().get(TradingPair.class, id);
    }

    public Currency getCurrency(long id) {
        return (Currency) sessionFactory.getCurrentSession().get(Currency.class, id);
    }
}
