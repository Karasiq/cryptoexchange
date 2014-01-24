package com.springapp.cryptoexchange.database;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class HistoryManager implements AbstractHistoryManager {
    private @Getter @Setter Period chartPeriod = new Period(1, 0, 0, 0);

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    SessionFactory sessionFactory;

    private final Map<TradingPair, List<Candle>> historyMap = new HashMap<>();
    public void init() {
        synchronized (historyMap) {
            List<TradingPair> currencyList = settingsManager.getTradingPairs();
            for(TradingPair tradingPair : currencyList) {
                historyMap.put(tradingPair, getMarketChartData(tradingPair, 24));
            }
        }
    }

    @Transactional
    public void updateChartData(@NonNull TradingPair tradingPair, final BigDecimal lastPrice, final BigDecimal amount) {
        List<Candle> history;
        Candle lastCandle;
        synchronized (historyMap) {
            history = historyMap.get(tradingPair);
            if (history == null) {
                history = new ArrayList<>();
                historyMap.put(tradingPair, history);
            }
        }
        synchronized (history) {
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
                .addOrder(org.hibernate.criterion.Order.desc("open_time"))
                .setMaxResults(max)
                .list();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Candle> getMarketChartData(TradingPair tradingPair, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Candle.class)
                .setMaxResults(max)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .list();
    }
}
