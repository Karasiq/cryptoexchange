package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Candle;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.utils.CacheCleaner;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HistoryManagerImpl implements HistoryManager {
    static final Period chartPeriod = Period.hours(1);

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    CacheCleaner cacheCleaner;

    final Map<Long, Candle> historyMap = new ConcurrentHashMap<>(); // Cache

    private void updateChartData(@NonNull TradingPair tradingPair, final BigDecimal lastPrice, final BigDecimal amount) {
        Candle lastCandle = historyMap.get(tradingPair.getId());
        if (lastCandle == null) {
            lastCandle = new Candle(tradingPair);
            historyMap.put(tradingPair.getId(), lastCandle);
        }
        lastCandle.update(lastPrice, amount);
        if (lastCandle.isClosed(chartPeriod)) { // next
            lastCandle.close();
            sessionFactory.getCurrentSession().saveOrUpdate(lastCandle);
            lastCandle = new Candle(tradingPair, lastPrice);
            historyMap.put(tradingPair.getId(), lastCandle);
        }
        sessionFactory.getCurrentSession().saveOrUpdate(lastCandle);
    }

    @Transactional
    @Async
    public void updateMarketInfo(@NonNull TradingPair tradingPair, final BigDecimal price, final BigDecimal amount) {
        Session session = sessionFactory.getCurrentSession();
        tradingPair = (TradingPair) session.load(TradingPair.class, tradingPair.getId());
        if(tradingPair.getLastReset() == null || DateTime.now().minus(Period.days(1)).isAfter(new DateTime(tradingPair.getLastReset()))) {
            tradingPair.setLastReset(new Date());
            tradingPair.setDayHigh(null);
            tradingPair.setDayLow(null);
            tradingPair.setVolume(BigDecimal.ZERO);
        }

        BigDecimal low = tradingPair.getDayLow(), high = tradingPair.getDayHigh(), volume = tradingPair.getVolume();
        if(low == null || low.equals(BigDecimal.ZERO) || price.compareTo(low) < 0) {
            tradingPair.setDayLow(price);
        }
        if(high == null || price.compareTo(high) > 0) {
            tradingPair.setDayHigh(price);
        }
        tradingPair.setLastPrice(price);
        tradingPair.setVolume(volume == null ? amount : volume.add(amount));
        updateChartData(tradingPair, price, amount);
        session.update(tradingPair);
        cacheCleaner.marketPricesEvict(tradingPair);
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
    @SuppressWarnings("unchecked")
    public List<Candle> getMarketChartData(@NonNull TradingPair tradingPair, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Candle.class)
                .setMaxResults(max)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .addOrder(org.hibernate.criterion.Order.desc("openTime"))
                .list();
    }

    @Autowired
    PlatformTransactionManager transactionManager;

    @PostConstruct
    public void init() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                List<TradingPair> currencyList = settingsManager.getTradingPairs();
                for(TradingPair tradingPair : currencyList) {
                    List<Candle> candles = getMarketChartData(tradingPair, 1);
                    if(candles.size() > 0) historyMap.put(tradingPair.getId(), candles.get(0));
                }
            }
        });
    }
}
