package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Candle;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Transactional
public class HistoryManager implements AbstractHistoryManager {
    private @Getter @Setter Period chartPeriod = new Period(1, 0, 0, 0);

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    SessionFactory sessionFactory;

    private final Map<TradingPair, AtomicReference<List<Candle>>> historyMap = new ConcurrentHashMap<>();
    public void init() {
        List<TradingPair> currencyList = settingsManager.getTradingPairs();
        for(TradingPair tradingPair : currencyList) {
            historyMap.put(tradingPair, new AtomicReference<>(getMarketChartData(tradingPair, 24)));
        }
    }

    @Transactional
    @Async
    public void updateChartData(@NonNull TradingPair tradingPair, final BigDecimal lastPrice, final BigDecimal amount) {
        AtomicReference<List<Candle>> historyAtomicRef = historyMap.get(tradingPair);
        if (historyAtomicRef == null) {
            historyAtomicRef = new AtomicReference<>((List<Candle>) new ArrayList<Candle>());
            historyMap.put(tradingPair, historyAtomicRef);
        }
        List<Candle> history = historyAtomicRef.get();
        Candle lastCandle;
        if (history.isEmpty()) {
            lastCandle = new Candle(tradingPair);
            history.add(lastCandle);
        } else {
            lastCandle = history.get(history.size() - 1);
        }
        lastCandle.update(lastPrice, amount);
        if (lastCandle.isClosed(chartPeriod)) { // next
            lastCandle.close();
            sessionFactory.getCurrentSession().saveOrUpdate(lastCandle);

            lastCandle = new Candle(tradingPair, lastPrice);
            history.add(lastCandle);
        }
        while (history.size() > 100) {
            history.remove(0); // remove first (oldest) candle
        }
        sessionFactory.getCurrentSession().saveOrUpdate(lastCandle);
        historyAtomicRef.compareAndSet(history, history);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Order> getMarketHistory(@NonNull TradingPair tradingPair, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"))
                .setMaxResults(max)
                .list();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Order> getAccountHistory(@NonNull Account account, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .add(Restrictions.eq("account", account))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"))
                .setMaxResults(max)
                .list();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Order> getAccountHistoryByPair(@NonNull TradingPair tradingPair, @NonNull Account account, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .add(Restrictions.eq("account", account))
                .add(Restrictions.eq("tradingPair", tradingPair))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"))
                .setMaxResults(max)
                .list();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Candle> getMarketChartData(@NonNull TradingPair tradingPair, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Candle.class)
                .setMaxResults(max)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .list();
    }
}
