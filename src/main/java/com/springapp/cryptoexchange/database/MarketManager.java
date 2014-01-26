package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.Calculator;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.database.model.Order;
import lombok.NonNull;
import lombok.experimental.NonFinal;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.*;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@Transactional
public class MarketManager implements AbstractMarketManager {
    private Log log = LogFactory.getLog(MarketManager.class);

    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AbstractDaemonManager daemonManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractHistoryManager historyManager;

    private final Map<Long, Object> lockerMap = new HashMap<>();

    public void init() {
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
        if(order.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            order.setStatus(Order.Status.COMPLETED);
            order.setCloseDate(new Date());
        } else {
            order.setStatus(Order.Status.PARTIALLY_COMPLETED);
        }
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
        assert firstOrder.getType() == Order.Type.SELL && secondOrder.getType() == Order.Type.BUY; // First sell, then buy
        BigDecimal price = firstOrder.getPrice(), tradingFee = firstOrder.getTradingPair().getTradingFee();

        BigDecimal firstCurrencySend = Calculator.withoutFee(Calculator.buyTotal(amount, price), tradingFee), secondCurrencySend = Calculator.withoutFee(amount, tradingFee);
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
    public void cancelOrder(@NonNull Order order) throws Exception {
        assert order.getStatus() == Order.Status.OPEN || order.getStatus() == Order.Status.PARTIALLY_COMPLETED;
        synchronized (lockerMap.get(order.getTradingPair().getId())) {
            Session session = sessionFactory.getCurrentSession();
            order.setStatus(Order.Status.CANCELLED);
            session.saveOrUpdate(order);
            returnUnusedFunds(order);
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Order executeOrder(@NonNull Order newOrder) throws Exception {
        if (newOrder.getAmount().compareTo(newOrder.getTradingPair().getMinimalTradeAmount()) < 0) {
            throw new MarketError(String.format("Minimal trading amount is %s", newOrder.getTradingPair().getMinimalTradeAmount()));
        }
        synchronized (lockerMap.get(newOrder.getTradingPair().getId())) { // Critical
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
    public List<Order> getOrdersByAccount(@NonNull Account account, int max) { // only for information!!!
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .setMaxResults(max)
                .add(Restrictions.eq("account", account))
                .addOrder(org.hibernate.criterion.Order.desc("open_time"))
                .list();
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
