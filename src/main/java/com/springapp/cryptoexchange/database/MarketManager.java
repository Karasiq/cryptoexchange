package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.utils.CacheCleaner;
import com.springapp.cryptoexchange.utils.Calculator;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.utils.LockManager;
import lombok.NonNull;
import lombok.experimental.NonFinal;
import lombok.extern.apachecommons.CommonsLog;
import net.anotheria.idbasedlock.IdBasedLock;
import net.anotheria.idbasedlock.IdBasedLockManager;
import net.anotheria.idbasedlock.SafeIdBasedLockManager;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


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
    AbstractFeeManager feeManager;

    @Autowired
    AbstractAccountManager accountManager;

    @Autowired
    CacheCleaner cacheCleaner;

    @Autowired
    LockManager lockManager;

    private void updateOrderStatus(@NonFinal Order order) {
        final Date now = new Date();
        if(order.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            order.setStatus(Order.Status.COMPLETED);
            order.setCloseDate(now);
        } else {
            order.setStatus(Order.Status.PARTIALLY_COMPLETED);
        }
        order.setUpdateDate(now);
    }

    private void returnUnusedFunds(@NonNull Order order) {
        Session session = sessionFactory.getCurrentSession();
        assert !order.isActual();
        BigDecimal returnAmount = Calculator.totalRequired(order.getType(), order.getAmount(), order.getPrice()).subtract(order.getTotal());
        if (returnAmount.compareTo(BigDecimal.ZERO) != 0) {
            VirtualWallet wallet = order.getSourceWallet();
            session.refresh(wallet);
            wallet.addBalance(returnAmount);
            session.update(wallet);
            log.info(String.format("[MarketManager] Returned unspent money (#%d): %s", order.getId(), returnAmount));
            cacheCleaner.balancesEvict(order.getAccount());
        }
    }

    @Async
    private void updateMarketInfo(@NonFinal TradingPair tradingPair, final BigDecimal price, final BigDecimal amount) {
        Session session = sessionFactory.getCurrentSession();
        tradingPair = (TradingPair) session.merge(tradingPair);
        if(tradingPair.getLastReset() == null || DateTime.now().minus(Period.days(1)).isAfter(new DateTime(tradingPair.getLastReset()))) {
            tradingPair.setLastReset(new Date());
            tradingPair.setDayHigh(null);
            tradingPair.setDayLow(null);
            tradingPair.setVolume(BigDecimal.ZERO);
        }

        BigDecimal low = tradingPair.getDayLow(), high = tradingPair.getDayHigh(), volume = tradingPair.getVolume();
        if(low == null || price.compareTo(low) < 0) {
            tradingPair.setDayLow(price);
        }
        if(high == null || price.compareTo(high) > 0) {
            tradingPair.setDayHigh(price);
        }
        tradingPair.setLastPrice(price);
        tradingPair.setVolume(volume == null ? amount : volume.add(amount));
        historyManager.updateChartData(tradingPair, price, amount);
        session.update(tradingPair);
        cacheCleaner.marketPricesEvict(tradingPair);
    }

    @SuppressWarnings("all")
    private void remapFunds(@NonFinal Order firstOrder, @NonFinal Order secondOrder, final BigDecimal amount) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        TradingPair tradingPair = firstOrder.getTradingPair();
        assert firstOrder.getType() == Order.Type.SELL && secondOrder.getType() == Order.Type.BUY; // First sell, then buy
        assert firstOrder.isActual() && secondOrder.isActual() && firstOrder.getSourceWallet().getCurrency().equals(tradingPair.getFirstCurrency()) && firstOrder.getDestWallet().getCurrency().equals(tradingPair.getSecondCurrency()) && secondOrder.getSourceWallet().getCurrency().equals(tradingPair.getSecondCurrency()) && secondOrder.getDestWallet().getCurrency().equals(tradingPair.getFirstCurrency());
        BigDecimal price = firstOrder.getPrice(), tradingFee = tradingPair.getTradingFee(), total = Calculator.buyTotal(amount, price);

        BigDecimal firstCurrencySend = Calculator.withoutFee(amount, tradingFee), secondCurrencySend = Calculator.withoutFee(total, tradingFee);

        Currency firstCurrency = tradingPair.getFirstCurrency(), secondCurrency = tradingPair.getSecondCurrency();
        log.info(String.format("Trade occured: %s %s @ %s %s (total %s %s)", amount, firstCurrency.getCurrencyCode(), price, secondCurrency.getCurrencyCode(), total, secondCurrency.getCurrencyCode()));

        firstOrder.addTotal(amount);
        firstOrder.addCompletedAmount(amount);
        secondOrder.addTotal(total);
        secondOrder.addCompletedAmount(amount);
        updateOrderStatus(firstOrder);
        updateOrderStatus(secondOrder);

        if(firstOrder.getStatus() == Order.Status.COMPLETED || secondOrder.getStatus() == Order.Status.COMPLETED) {
            updateMarketInfo(tradingPair, price, amount);
        }

        VirtualWallet firstDest = firstOrder.getDestWallet(), secondDest = secondOrder.getDestWallet();
        firstDest.addBalance(firstCurrencySend);
        secondDest.addBalance(secondCurrencySend);
        session.update(firstDest);
        session.update(secondDest);

        if(firstOrder.getStatus() == Order.Status.COMPLETED) {
            returnUnusedFunds(firstOrder);
        }
        if(secondOrder.getStatus() == Order.Status.COMPLETED) {
            returnUnusedFunds(secondOrder);
        }

        feeManager.submitCollectedFee(FreeBalance.FeeType.TRADING, tradingPair.getFirstCurrency(), Calculator.fee(amount, tradingFee));
        feeManager.submitCollectedFee(FreeBalance.FeeType.TRADING, tradingPair.getSecondCurrency(), Calculator.fee(total, tradingFee));
        cacheCleaner.orderExecutionEvict(firstOrder, secondOrder);
    }

    @Transactional
    public Order getOrder(long orderId) {
        return (Order) sessionFactory.getCurrentSession().get(Order.class, orderId);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    @Caching(evict = {
            @CacheEvict(value = "getAccountOrdersByPair", key = "#order.account.login + #order.tradingPair.id"),
            @CacheEvict(value = "getAccountOrders", key = "#order.account.login"),
            @CacheEvict(value = "getAccountBalances", key = "#order.account.login"),
            @CacheEvict(value = "getMarketDepth", key = "#order.tradingPair.id")
    })
    public void cancelOrder(@NonNull Order order) throws Exception {
        assert order.isActual();
        IdBasedLockManager<Long> currencyLockManager = lockManager.getCurrencyLockManager();
        IdBasedLock<Long> lock = currencyLockManager.obtainLock(order.getTradingPair().getFirstCurrency().getId()), lock1 = currencyLockManager.obtainLock(order.getTradingPair().getSecondCurrency().getId());
        lock.lock();
        lock1.lock();
        try {
            log.info(String.format("cancelOrder => %s", order));
            Session session = sessionFactory.getCurrentSession();
            order.cancel();
            returnUnusedFunds(order);
            session.update(order);
        } catch (Exception e) {
            log.error("cancelOrder error", e);
            throw e;
        } finally {
            lock.unlock();
            lock1.unlock();
        }
    }

    @Caching(evict = {
            @CacheEvict(value = "getMarketDepth", key = "#newOrder.tradingPair.id"),
            @CacheEvict(value = "getMarketHistory", key = "#newOrder.tradingPair.id"),
            @CacheEvict(value = "getAccountOrdersByPair", key = "#newOrder.account.login + #newOrder.tradingPair.id"),
            @CacheEvict(value = "getAccountOrders", key = "#newOrder.account.login"),
            @CacheEvict(value = "getAccountBalances", key = "#newOrder.account.login")
    })
    @Transactional
    @SuppressWarnings("unchecked")
    public Order executeOrder(@NonNull Order newOrder) throws Exception {
        newOrder.setAmount(newOrder.getAmount().setScale(8, BigDecimal.ROUND_FLOOR));
        newOrder.setPrice(newOrder.getPrice().setScale(8, BigDecimal.ROUND_FLOOR));
        assert newOrder.getTradingPair() != null && newOrder.getTradingPair().isEnabled() && newOrder.getPrice().compareTo(BigDecimal.ZERO) > 0 && newOrder.getPrice().compareTo(BigDecimal.ZERO) > 0;
        if (newOrder.getAmount().compareTo(newOrder.getTradingPair().getMinimalTradeAmount()) < 0) {
            throw new MarketError(String.format("Minimal trading amount is %s", newOrder.getTradingPair().getMinimalTradeAmount()));
        }
        IdBasedLockManager<Long> currencyLockManager = lockManager.getCurrencyLockManager();
        IdBasedLock<Long> lock = currencyLockManager.obtainLock(newOrder.getTradingPair().getFirstCurrency().getId()), lock1 = currencyLockManager.obtainLock(newOrder.getTradingPair().getSecondCurrency().getId());
        lock.lock();
        lock1.lock();
        try {
            Session session = sessionFactory.getCurrentSession();
            log.info(String.format("executeOrder => %s", newOrder));
            Order.Type orderType = newOrder.getType();
            VirtualWallet virtualWalletSource = newOrder.getSourceWallet(), virtualWalletDest = newOrder.getDestWallet();
            session.refresh(virtualWalletSource);
            session.refresh(virtualWalletDest);
            BigDecimal balance = accountManager.getVirtualWalletBalance(virtualWalletSource);
            BigDecimal remainingAmount = newOrder.getRemainingAmount();
            BigDecimal required = Calculator.totalRequired(orderType, remainingAmount, newOrder.getPrice());

            if(balance.compareTo(required) < 0) {
                throw new MarketError("Insufficient funds");
            } else {
                virtualWalletSource.addBalance(required.negate()); // Lock funds
            }

            List<Order> orders = session.createCriteria(Order.class)
                    .addOrder(orderType == Order.Type.BUY ? org.hibernate.criterion.Order.asc("price") : org.hibernate.criterion.Order.desc("price"))
                    .addOrder(org.hibernate.criterion.Order.asc("openDate"))
                    .add(Restrictions.in("status", new Order.Status[]{Order.Status.OPEN, Order.Status.PARTIALLY_COMPLETED}))
                    .add(Restrictions.eq("tradingPair", newOrder.getTradingPair()))
                    .add(orderType == Order.Type.BUY ? Restrictions.le("price", newOrder.getPrice()) : Restrictions.ge("price", newOrder.getPrice()))
                    .add(Restrictions.eq("type", orderType.equals(Order.Type.BUY) ? Order.Type.SELL : Order.Type.BUY))
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
                session.update(order);
                if(newOrder.getStatus().equals(Order.Status.COMPLETED)) {
                    break; // Order executed
                }
            }
            session.save(newOrder);
            return newOrder;
        } catch (Exception e) {
            log.error("executeOrder error", e);
            throw e;
        } finally {
            lock.unlock();
            lock1.unlock();
        }
    }

    @Transactional
    public void setTradingPairEnabled(TradingPair tradingPair, boolean enabled) {
        Session session = sessionFactory.getCurrentSession();
        tradingPair.setEnabled(enabled);
        session.update(tradingPair);
        log.info(String.format("setTradingPairEnabled: %s", tradingPair));
    }


    @Transactional
    @SuppressWarnings("unchecked")
    public List<Order> getOpenOrders(TradingPair tradingPair, Order.Type type, int max, boolean ascending) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .setMaxResults(max)
                .add(Restrictions.eq("tradingPair", tradingPair))
                .add(Restrictions.in("status", new Order.Status[] { Order.Status.OPEN, Order.Status.PARTIALLY_COMPLETED }))
                .add(Restrictions.eq("type", type))
                .addOrder(ascending ? org.hibernate.criterion.Order.asc("price") : org.hibernate.criterion.Order.desc("price"))
                .list();
    }
}
