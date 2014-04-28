package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Candle;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.utils.CacheCleaner;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Repository
@FieldDefaults(level = AccessLevel.PRIVATE)
@CommonsLog
public class HistoryManagerImpl implements HistoryManager {
    static final Period chartPeriod = Period.hours(1);

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    CacheCleaner cacheCleaner;

    @SuppressWarnings("unchecked")
    private void updateChartData(@NonNull TradingPair tradingPair, final BigDecimal lastPrice, final BigDecimal amount) {
        Session session = sessionFactory.getCurrentSession();
        final List<Candle> candleList = session.createCriteria(Candle.class)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .add(Restrictions.isNull("closeTime"))
                .addOrder(org.hibernate.criterion.Order.desc("openTime"))
                .setMaxResults(1)
                .list();

        Candle lastCandle = candleList == null || candleList.isEmpty() ? new Candle(tradingPair) : candleList.get(0);
        lastCandle.update(lastPrice, amount);
        if (lastCandle.isClosed(chartPeriod)) { // next
            lastCandle.close();
            session.saveOrUpdate(lastCandle);
            log.info("Closed: " + lastCandle);
            lastCandle = new Candle(tradingPair, lastPrice);
        }
        session.saveOrUpdate(lastCandle);
        log.info(lastCandle);
    }

    @Transactional
    @Async
    public void updateMarketInfo(@NonNull TradingPair tradingPair, final BigDecimal price, final BigDecimal amount) {
        Session session = sessionFactory.getCurrentSession();
        tradingPair = (TradingPair) session.get(TradingPair.class, tradingPair.getId());
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

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Criteria getMarketHistory(@NonNull TradingPair tradingPair) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .add(Restrictions.in("status", Arrays.asList(Order.Status.COMPLETED, Order.Status.PARTIALLY_CANCELLED)))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"));
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Order> getAccountHistory(@NonNull Account account, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .add(Restrictions.eq("account", account))
                .add(Restrictions.in("status", Arrays.asList(Order.Status.COMPLETED, Order.Status.PARTIALLY_CANCELLED)))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"))
                .setMaxResults(max)
                .list();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Order> getAccountHistoryByPair(@NonNull TradingPair tradingPair, @NonNull Account account, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .add(Restrictions.eq("account", account))
                .add(Restrictions.eq("tradingPair", tradingPair))
                .add(Restrictions.in("status", Arrays.asList(Order.Status.COMPLETED, Order.Status.PARTIALLY_CANCELLED)))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"))
                .setMaxResults(max)
                .list();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Candle> getMarketChartData(@NonNull TradingPair tradingPair, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Candle.class)
                .setMaxResults(max)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .addOrder(org.hibernate.criterion.Order.desc("openTime"))
                .list();
    }
}
