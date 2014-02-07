package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Candle;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.Cleanup;
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

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class HistoryManager implements AbstractHistoryManager {
    private @Getter @Setter Period chartPeriod = new Period(1, 0, 0, 0);

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    SessionFactory sessionFactory;

    private final Map<Long, List<Candle>> historyMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        @Cleanup Session session = sessionFactory.openSession();
        List<TradingPair> currencyList = settingsManager.getTradingPairs();
        for(TradingPair tradingPair : currencyList) {
            historyMap.put(tradingPair.getId(), getMarketChartData(session, tradingPair, 24));
        }
    }

    @Transactional
    @Async
    public void updateChartData(@NonNull TradingPair tradingPair, final BigDecimal lastPrice, final BigDecimal amount) {
        List<Candle> history = historyMap.get(tradingPair.getId());
        synchronized (history) {
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
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Order> getMarketHistory(@NonNull TradingPair tradingPair, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .add(Restrictions.in("status", new Order.Status[] { Order.Status.COMPLETED, Order.Status.PARTIALLY_CANCELLED }))
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
                .add(Restrictions.in("status", new Order.Status[] { Order.Status.COMPLETED, Order.Status.PARTIALLY_CANCELLED }))
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
                .add(Restrictions.in("status", new Order.Status[] { Order.Status.COMPLETED, Order.Status.PARTIALLY_CANCELLED }))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"))
                .setMaxResults(max)
                .list();
    }

    @Transactional
    public List<Candle> getMarketChartData(@NonNull TradingPair tradingPair, int max) {
        return getMarketChartData(sessionFactory.getCurrentSession(), tradingPair, max);
    }

    @SuppressWarnings("unchecked")
    private List<Candle> getMarketChartData(Session session, TradingPair tradingPair, int max) {
        return session.createCriteria(Candle.class)
                .setMaxResults(max)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .list();
    }
}
