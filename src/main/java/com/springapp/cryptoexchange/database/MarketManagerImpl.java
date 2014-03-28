package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.utils.CacheCleaner;
import com.springapp.cryptoexchange.utils.Calculator;
import lombok.NonNull;
import lombok.experimental.NonFinal;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Repository
@CommonsLog
public class MarketManagerImpl implements MarketManager {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    DaemonManager daemonManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    HistoryManager historyManager;

    @Autowired
    FeeManager feeManager;

    @Autowired
    AccountManager accountManager;

    @Autowired
    CacheCleaner cacheCleaner;

    private void returnUnusedFunds(@NonNull Order order) {
        Session session = sessionFactory.getCurrentSession();
        Assert.isTrue(!order.isActual(), "Order must be closed");
        BigDecimal returnAmount = Calculator.totalRequired(order.getType(), order.getAmount(), order.getPrice()).subtract(order.getTotal());
        if (returnAmount.compareTo(BigDecimal.ZERO) != 0) {
            VirtualWallet wallet = order.getSourceWallet();
            wallet.addBalance(returnAmount);
            session.update(wallet);
            log.info(String.format("Returned unspent money (#%d): %s", order.getId(), returnAmount));
            cacheCleaner.balancesEvict(order.getAccount());
        }
    }

    @SuppressWarnings("all")
    private void remapFunds(@NonFinal Order firstOrder, @NonFinal Order secondOrder, final BigDecimal amount) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        TradingPair tradingPair = firstOrder.getTradingPair();
        Assert.isTrue(
                // Orders matching:
                firstOrder.getType() == Order.Type.SELL && secondOrder.getType() == Order.Type.BUY // First sell, then buy
                && firstOrder.isActual() && secondOrder.isActual() // Orders not closed
                // Currencies matching:
                && firstOrder.getSourceWallet().getCurrency().equals(tradingPair.getFirstCurrency())
                && firstOrder.getDestWallet().getCurrency().equals(tradingPair.getSecondCurrency())
                && secondOrder.getSourceWallet().getCurrency().equals(tradingPair.getSecondCurrency())
                && secondOrder.getDestWallet().getCurrency().equals(tradingPair.getFirstCurrency()), "Invalid parameters");

        boolean zeroFee = firstOrder.getAccount().getRole().equals(Account.RoleClass.ADMIN) || secondOrder.getAccount().getRole().equals(Account.RoleClass.ADMIN);
        if (zeroFee) {
            log.info(String.format("Zero-fee trade: %s => %s", firstOrder, secondOrder));
        }

        BigDecimal price = firstOrder.getPrice(),
                tradingFee = tradingPair.getTradingFee(),
                total = Calculator.buyTotal(amount, price),
                firstCurrencySend = zeroFee ? amount : Calculator.withoutFee(amount, tradingFee),
                secondCurrencySend = zeroFee ? total : Calculator.withoutFee(total, tradingFee);

        Currency firstCurrency = tradingPair.getFirstCurrency(), secondCurrency = tradingPair.getSecondCurrency();
        Assert.isTrue(firstCurrency != null && secondCurrency != null, "Currency not found");
        log.info(String.format("Trade occured: %s %s @ %s %s (total %s %s)", amount, firstCurrency.getCurrencyCode(), price, secondCurrency.getCurrencyCode(), total, secondCurrency.getCurrencyCode()));


        // Updating orders:
        firstOrder.addTotal(amount);
        firstOrder.addCompletedAmount(amount);
        secondOrder.addTotal(total);
        secondOrder.addCompletedAmount(amount);
        firstOrder.updateCompletionStatus();
        secondOrder.updateCompletionStatus();


        // Updating market info:
        if(firstOrder.getStatus() == Order.Status.COMPLETED || secondOrder.getStatus() == Order.Status.COMPLETED) {
            historyManager.updateMarketInfo(tradingPair, price, amount);
        }

        // Updating balances:
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


        // Collected fee:
        if (!zeroFee) {
            feeManager.submitCollectedFee(FreeBalance.FeeType.TRADING, tradingPair.getFirstCurrency(), Calculator.fee(amount, tradingFee));
            feeManager.submitCollectedFee(FreeBalance.FeeType.TRADING, tradingPair.getSecondCurrency(), Calculator.fee(total, tradingFee));
        }

        // Uncache all:
        cacheCleaner.orderExecutionEvict(firstOrder, secondOrder);
    }

    @Transactional(readOnly = true)
    public Order getOrder(long orderId) {
        return (Order) sessionFactory.getCurrentSession().get(Order.class, orderId);
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @SuppressWarnings("unchecked")
    @Caching(evict = {
            @CacheEvict(value = "getAccountOrdersByPair", key = "#order.account.login + #order.tradingPair.id"),
            @CacheEvict(value = "getAccountOrders", key = "#order.account.login"),
            @CacheEvict(value = "getAccountBalances", key = "#order.account.login"),
            @CacheEvict(value = "getMarketDepth", key = "#order.tradingPair.id")
    })
    public void cancelOrder(@NonNull Order order) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        Assert.isTrue(order.isActual(), "Order already closed");
        log.info(String.format("cancelOrder => %s", order));
        order.cancel(); // Change order status
        returnUnusedFunds(order); // Return money
        session.update(order);
    }

    @Caching(evict = {
            @CacheEvict(value = "getMarketDepth", key = "#newOrder.tradingPair.id"),
            @CacheEvict(value = "getMarketHistory", key = "#newOrder.tradingPair.id"),
            @CacheEvict(value = "getAccountOrdersByPair", key = "#newOrder.account.login + #newOrder.tradingPair.id"),
            @CacheEvict(value = "getAccountOrders", key = "#newOrder.account.login"),
            @CacheEvict(value = "getAccountBalances", key = "#newOrder.account.login")
    })
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @SuppressWarnings("unchecked")
    public Order executeOrder(@NonNull Order newOrder) throws Exception {
        final long start = System.nanoTime();
        // Normalizing:
        newOrder.setAmount(newOrder.getAmount().setScale(8, BigDecimal.ROUND_FLOOR));
        newOrder.setPrice(newOrder.getPrice().setScale(8, BigDecimal.ROUND_FLOOR));

        // Checking input parameters:
        Assert.isTrue(
                // Checking order:
                newOrder.isActual() && newOrder.getId() == 0
                // Checking trading pair:
                && newOrder.getTradingPair() != null && newOrder.getTradingPair().isEnabled()
                // Checking price:
                && newOrder.getPrice().compareTo(BigDecimal.ZERO) > 0, "Invalid parameters"
        );

        if (newOrder.getAmount().compareTo(newOrder.getTradingPair().getMinimalTradeAmount()) < 0) {
            throw new MarketError(String.format("Minimal trading amount is %s", newOrder.getTradingPair().getMinimalTradeAmount()));
        }

        final Session session = sessionFactory.getCurrentSession();
        log.info(String.format("executeOrder => %s", newOrder));
        final Order.Type orderType = newOrder.getType();

        VirtualWallet virtualWalletSource = newOrder.getSourceWallet();
        final BigDecimal balance = accountManager.getVirtualWalletBalance(virtualWalletSource), remainingAmount = newOrder.getRemainingAmount(), required = Calculator.totalRequired(orderType, remainingAmount, newOrder.getPrice());

        if(balance.compareTo(required) < 0) {
            throw new MarketError("Insufficient funds");
        } else {
            virtualWalletSource.addBalance(required.negate()); // Lock funds
        }

        // Retrieving relevant orders:
        List<Order> orders = session.createCriteria(Order.class)
                .setFetchSize(20)
                .setFetchMode("tradingPair", FetchMode.JOIN)
                .setFetchMode("sourceWallet", FetchMode.JOIN)
                .setFetchMode("destWallet", FetchMode.JOIN)
                .setFetchMode("account", FetchMode.JOIN)
                .addOrder(orderType == Order.Type.BUY ? org.hibernate.criterion.Order.asc("price") : org.hibernate.criterion.Order.desc("price"))
                .addOrder(org.hibernate.criterion.Order.asc("openDate"))
                .add(Restrictions.in("status", new Order.Status[]{Order.Status.OPEN, Order.Status.PARTIALLY_COMPLETED}))
                .add(Restrictions.eq("tradingPair", newOrder.getTradingPair()))
                .add(orderType.equals(Order.Type.BUY) ? Restrictions.le("price", newOrder.getPrice()) : Restrictions.ge("price", newOrder.getPrice()))
                .add(Restrictions.eq("type", orderType.equals(Order.Type.BUY) ? Order.Type.SELL : Order.Type.BUY))
                .list();

        // Performing trade:
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
        log.info(String.format("Order executed in %d ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
        return newOrder;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Criteria getOpenOrders(TradingPair tradingPair, Order.Type orderType) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .addOrder(orderType.equals(Order.Type.SELL) ? org.hibernate.criterion.Order.asc("price") : org.hibernate.criterion.Order.desc("price"))
                .addOrder(org.hibernate.criterion.Order.asc("openDate"))
                .add(Restrictions.eq("tradingPair", tradingPair))
                .add(Restrictions.in("status", Arrays.asList(Order.Status.OPEN, Order.Status.PARTIALLY_COMPLETED)))
                .add(Restrictions.eq("type", orderType));
    }

    @Transactional
    @Scheduled(cron = "30 4 * * 1 *") // Sunday 4:30
    public void cleanOrders() {
        log.info("Orders auto-clean started");
        Session session = sessionFactory.getCurrentSession();
        int affectedRows = session.createQuery("delete from Order where closeDate <= :time and status in (:statuses)")
                .setDate("time", DateTime.now().minus(Period.months(1)).toDate())
                .setParameterList("statuses", Arrays.asList(Order.Status.CANCELLED, Order.Status.PARTIALLY_CANCELLED, Order.Status.COMPLETED))
                .executeUpdate();
        log.info(String.format("Orders deleted: %d", affectedRows));
    }
}
