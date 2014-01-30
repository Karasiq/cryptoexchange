package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.Calculator;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.webapi.AbstractConvertService;
import lombok.NonNull;
import lombok.experimental.NonFinal;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Transactional
@CommonsLog
public class MarketManager implements AbstractMarketManager {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AbstractDaemonManager daemonManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractConvertService convertService;

    private final Map<Long, Object> lockerMap = new ConcurrentHashMap<>();

    public void reloadTradingPairs() {
        synchronized (lockerMap) {
            List<TradingPair> currencyList = settingsManager.getTradingPairs();
            for(TradingPair tradingPair : currencyList) {
                if(!lockerMap.containsKey(tradingPair.getId())) {
                    lockerMap.put(tradingPair.getId(), new Object());
                }
            }
        }
    }

    private static void updateOrderStatus(@NonFinal Order order) {
        final Date now = new Date();
        if(order.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            order.setStatus(Order.Status.COMPLETED);
            order.setCloseDate(now);
        } else {
            order.setStatus(Order.Status.PARTIALLY_COMPLETED);
        }
        order.setUpdateDate(now);
    }

    @Transactional
    private void returnUnusedFunds(@NonNull final Order order) {
        Session session = sessionFactory.getCurrentSession();
        assert order.getStatus() == Order.Status.CANCELLED || order.getStatus() == Order.Status.COMPLETED;
        BigDecimal total = order.getTotal();
        BigDecimal returnAmount = Calculator.totalRequired(order.getType(), order.getAmount(), order.getPrice(), order.getTradingPair().getTradingFee()).subtract(total);
        VirtualWallet wallet = order.getSourceWallet();
        wallet.addBalance(returnAmount);
        session.saveOrUpdate(wallet);
    }

    @Transactional
    @Async
    @Caching(evict = {@CacheEvict(value = "getMarketPrices", key = "#tradingPair.id")})
    private void updateMarketInfo(@NonFinal TradingPair tradingPair, final BigDecimal price, final BigDecimal amount) {
        Session session = sessionFactory.getCurrentSession();
        session.update(tradingPair);

        if(tradingPair.getLastReset() == null || DateTime.now().minus(Period.days(1)).isAfter(new DateTime(tradingPair.getLastReset()))) {
            tradingPair.setLastReset(new Date());
            tradingPair.setDayHigh(BigDecimal.ZERO);
            tradingPair.setDayLow(BigDecimal.ZERO);
            tradingPair.setVolume(BigDecimal.ZERO);
        }

        BigDecimal low = tradingPair.getDayLow(), high = tradingPair.getDayHigh(), volume = tradingPair.getVolume();
        if(low == null || price.compareTo(low) < 0) {
            tradingPair.setDayLow(price);
        }
        if(high == null || price.compareTo(high) > 0) {
            tradingPair.setDayHigh(price);
        }
        if(volume == null) {
            volume = amount;
        }
        else {
            volume = volume.add(amount);
        }
        tradingPair.setLastPrice(price);
        tradingPair.setVolume(volume);
        session.saveOrUpdate(tradingPair);
        historyManager.updateChartData(tradingPair, price, amount);
    }

    @SuppressWarnings("all")
    private void remapFunds(@NonFinal Order firstOrder, @NonFinal Order secondOrder, final BigDecimal amount) throws Exception {
        TradingPair tradingPair = firstOrder.getTradingPair();
        assert firstOrder.getType() == Order.Type.SELL && secondOrder.getType() == Order.Type.BUY; // First sell, then buy
        BigDecimal price = firstOrder.getPrice(), tradingFee = tradingPair.getTradingFee();

        BigDecimal firstCurrencySend = Calculator.withoutFee(amount, tradingFee), secondCurrencySend = Calculator.withoutFee(Calculator.buyTotal(amount, price), tradingFee);

        Currency firstCurrency = tradingPair.getFirstCurrency(), secondCurrency = tradingPair.getSecondCurrency();
        log.debug(String.format("[MarketManager] Trade occured: %s %s @ %s %s (total %s %s)", amount, firstCurrency.getCurrencyCode(), price, secondCurrency.getCurrencyCode(), secondCurrencySend, secondCurrency.getCurrencyCode()));

        firstOrder.addTotal(amount);
        firstOrder.addCompletedAmount(amount);
        secondOrder.addTotal(amount.multiply(price));
        secondOrder.addCompletedAmount(amount);
        updateOrderStatus(firstOrder);
        updateOrderStatus(secondOrder);
        if(firstOrder.getStatus() == Order.Status.COMPLETED || secondOrder.getStatus() == Order.Status.COMPLETED) {
            updateMarketInfo(firstOrder.getTradingPair(), price, amount);
        }

        VirtualWallet firstDest = firstOrder.getDestWallet(), secondDest = secondOrder.getDestWallet();
        // firstSource.addBalance(secondCurrencySend.negate()); // already locked
        firstDest.addBalance(firstCurrencySend);
        // secondSource.addBalance(firstCurrencySend.negate()); // already locked
        secondDest.addBalance(secondCurrencySend);

        if(firstOrder.getStatus() == Order.Status.COMPLETED) {
            returnUnusedFunds(firstOrder);
        }
        if(secondOrder.getStatus() == Order.Status.COMPLETED) {
            returnUnusedFunds(secondOrder);
        }
        // That's all !
    }

    @Transactional
    @SuppressWarnings("unchecked")
    @Caching(evict = { @CacheEvict(value = "getMarketDepth", key = "#newOrder.tradingPair.id") })
    public void cancelOrder(@NonNull Order order) throws Exception {
        assert order.getStatus() == Order.Status.OPEN || order.getStatus() == Order.Status.PARTIALLY_COMPLETED;
        synchronized (lockerMap.get(order.getTradingPair().getId())) {
            log.debug(String.format("[MarketManager] cancelOrder => %s", order));
            Session session = sessionFactory.getCurrentSession();
            order.setStatus(Order.Status.CANCELLED);
            session.saveOrUpdate(order);
            returnUnusedFunds(order);
        }
    }

    @Caching(evict = { @CacheEvict(value = "getMarketDepth", key = "#newOrder.tradingPair.id"), @CacheEvict(value = "getMarketHistory", key = "#newOrder.tradingPair.id") })
    @Transactional
    @SuppressWarnings("unchecked")
    public Order executeOrder(@NonNull Order newOrder) throws Exception {
        if (newOrder.getAmount().compareTo(newOrder.getTradingPair().getMinimalTradeAmount()) < 0) {
            throw new MarketError(String.format("Minimal trading amount is %s", newOrder.getTradingPair().getMinimalTradeAmount()));
        }
        synchronized (lockerMap.get(newOrder.getTradingPair().getId())) { // Critical
            log.debug(String.format("[MarketManager] executeOrder => %s", newOrder));
            Session session = sessionFactory.getCurrentSession();
            Order.Type orderType = newOrder.getType();
            VirtualWallet virtualWalletSource = newOrder.getSourceWallet(), virtualWalletDest = newOrder.getDestWallet();
            session.saveOrUpdate(virtualWalletSource);
            session.saveOrUpdate(virtualWalletDest);
            BigDecimal balance = virtualWalletSource.getBalance(daemonManager.getAccount(virtualWalletSource.getCurrency()));
            BigDecimal remainingAmount = newOrder.getRemainingAmount();
            BigDecimal required = Calculator.totalRequired(orderType, remainingAmount, newOrder.getPrice(), newOrder.getTradingPair().getTradingFee());

            if(balance.compareTo(required) < 0) {
                throw new MarketError("Insufficient funds");
            } else {
                virtualWalletSource.addBalance(required.negate()); // Lock funds
            }

            List<Order> orders = session.createCriteria(Order.class)
                    .addOrder(orderType == Order.Type.BUY ? org.hibernate.criterion.Order.asc("price") : org.hibernate.criterion.Order.desc("price"))
                    .addOrder(org.hibernate.criterion.Order.asc("openDate"))
                    .add(Restrictions.and(Restrictions.ne("status", Order.Status.CANCELLED), Restrictions.ne("status", Order.Status.COMPLETED)))
                    .add(Restrictions.eq("tradingPair", newOrder.getTradingPair()))
                    .add(orderType == Order.Type.BUY ? Restrictions.le("price", newOrder.getPrice()) : Restrictions.ge("price", newOrder.getPrice()))
                    .list();
            for(Order order : orders) {
                BigDecimal orderRemainingAmount = order.getRemainingAmount();
                int compareResult = orderRemainingAmount.compareTo(remainingAmount);
                final BigDecimal tradeAmount = compareResult >= 0 ? remainingAmount : orderRemainingAmount;
                if(order.getType().equals(Order.Type.SELL)) {
                    remapFunds(order, newOrder, tradeAmount);
                }
                else {
                    remapFunds(newOrder, order, tradeAmount);
                }
                session.saveOrUpdate(order);
                if(newOrder.getStatus().equals(Order.Status.COMPLETED)) {
                    break; // Order executed
                }
            }
            session.saveOrUpdate(newOrder);
            session.saveOrUpdate(virtualWalletSource);
            session.saveOrUpdate(virtualWalletDest);
            return newOrder;
        }
    }

    @Transactional
    public void setTradingPairEnabled(TradingPair tradingPair, boolean enabled) {
        Session session = sessionFactory.getCurrentSession();
        session.update(tradingPair);
        tradingPair.setEnabled(enabled);
        log.info(String.format("setTradingPairEnabled: %s", tradingPair));
        session.save(tradingPair);
    }


    @Transactional
    @SuppressWarnings("unchecked")
    public List<Order> getOpenOrders(TradingPair tradingPair, Order.Type type, int max, boolean ascending) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .setMaxResults(max)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .add(Restrictions.or(Restrictions.eq("status", Order.Status.OPEN), Restrictions.eq("status", Order.Status.PARTIALLY_COMPLETED)))
                .add(Restrictions.eq("type", type))
                .addOrder(ascending ? org.hibernate.criterion.Order.asc("price") : org.hibernate.criterion.Order.desc("price"))
                .list();
    }
}
