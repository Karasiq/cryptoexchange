package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;

@Repository
@CommonsLog
public class SettingsManagerImpl implements SettingsManager {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    @Lazy
    MarketManager marketManager;

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<TradingPair> getTradingPairs() {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(TradingPair.class)
                .list();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Currency> getCurrencyList() {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Currency.class)
                .list();
    }

    @Transactional(readOnly = true)
    public TradingPair getTradingPair(long id) {
        Session session = sessionFactory.getCurrentSession();
        return (TradingPair) session.get(TradingPair.class, id);
    }

    @Transactional(readOnly = true)
    public Currency getCurrency(long id) {
        Session session = sessionFactory.getCurrentSession();
        return (Currency) session.get(Currency.class, id);
    }

    @Transactional
    public void addCurrency(@NonNull Currency currency) throws Exception {
        Assert.hasLength(currency.getCurrencyCode(), "Currency code cannot be empty");
        Assert.hasLength(currency.getCurrencyName(), "Currency name cannot be empty");
        Session session = sessionFactory.getCurrentSession();
        session.save(currency);
        log.info("New currency added: " + currency);
    }

    @Transactional
    public void addTradingPair(@NonNull TradingPair tradingPair) throws Exception {
        Assert.isTrue(!tradingPair.getFirstCurrency().equals(tradingPair.getSecondCurrency()), "Invalid parameters");
        Session session = sessionFactory.getCurrentSession();
        session.save(tradingPair);
        log.info("New trading pair added: " + tradingPair);
    }

    @Caching(evict = {
            @CacheEvict(value = "getAccountOrdersByPair", allEntries = true),
            @CacheEvict(value = "getAccountOrders", allEntries = true),
            @CacheEvict(value = "getAccountBalances", allEntries = true),
            @CacheEvict(value = "getMarketDepth", key = "#tradingPair.id"),
            @CacheEvict(value = "getMarketHistory", key = "#tradingPair.id"),
            @CacheEvict(value = "getTradingPairs", allEntries = true),
            @CacheEvict(value = "getTradingPairInfo", key = "#tradingPair.id")
    })
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED)
    @SuppressWarnings("unchecked")
    public void removeTradingPair(TradingPair tradingPair) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        final List<Order> orderList = session.createCriteria(Order.class)
                .setFetchSize(200)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .add(Restrictions.in("status", Arrays.asList(Order.Status.OPEN, Order.Status.PARTIALLY_COMPLETED)))
                .list();
        for(Order order : orderList) marketManager.cancelOrder(order);
        session.delete(tradingPair);
        log.info("Trading pair removed: " + tradingPair);
    }
}
