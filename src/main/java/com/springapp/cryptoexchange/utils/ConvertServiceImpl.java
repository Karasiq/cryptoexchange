package com.springapp.cryptoexchange.utils;

import com.springapp.cryptoexchange.database.*;
import com.springapp.cryptoexchange.database.model.*;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Projections;
import org.hibernate.transform.ResultTransformer;
import org.hibernate.transform.Transformers;
import org.hibernate.type.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CommonsLog
@Service
public class ConvertServiceImpl implements ConvertService { // Convert layer
    @Autowired
    HistoryManager historyManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    MarketManager marketManager;

    @Autowired
    @Lazy
    DaemonManager daemonManager;

    @Autowired
    AccountManager accountManager;

    @Autowired
    SessionFactory sessionFactory;

    @SuppressWarnings("unchecked")
    private void makeDepth(Criteria criteria, List<Depth.Entry> list, int depthSize) {
        Depth.Entry depthEntry = new Depth.Entry();
        final List<Order> orderList = criteria.setProjection(Projections.projectionList()
                .add(Projections.property("price"), "price")
                .add(Projections.property("amount"), "amount")
                .add(Projections.property("completedAmount"), "completedAmount"))
                .setResultTransformer(Transformers.aliasToBean(Order.class))
                .setFetchSize(100)
                .setMaxResults(10000)
                .list();
        if(!orderList.isEmpty()) {
            for(Order order : orderList) {
                if(depthEntry.price != null && !depthEntry.price.equals(order.getPrice())) {
                    if(list.size() < depthSize) {
                        list.add(depthEntry);
                        depthEntry = new Depth.Entry();
                    } else {
                        break;
                    }
                }
                depthEntry.addOrder(order);
            }
            if (list.size() < depthSize && depthEntry.amount.compareTo(BigDecimal.ZERO) > 0 && (list.size() < 1 || !list.get(list.size() - 1).equals(depthEntry))) {
                list.add(depthEntry);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Depth createDepth(final @NonNull Criteria buyOrders, final @NonNull Criteria sellOrders, final int depthSize) throws Exception {
        final long start = System.nanoTime();
        final Depth depth = new Depth();
        makeDepth(buyOrders, depth.buyOrders, depthSize);
        makeDepth(sellOrders, depth.sellOrders, depthSize);
        log.info(String.format("Depth generated in %d ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
        return depth;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<MarketHistory> createHistory(@NonNull Criteria orders) throws Exception {
        return orders.setProjection(Projections.projectionList()
                .add(Projections.property("type"), "type")
                .add(Projections.property("price"), "price")
                .add(Projections.property("completedAmount"), "amount")
                .add(Projections.property("closeDate"), "time"))
                .setResultTransformer(Transformers.aliasToBean(MarketHistory.class))
                .list();
    }

    @Transactional
    public AccountBalanceInfo createAccountBalanceInfo(final @NonNull Account account) throws Exception {
        sessionFactory.getCurrentSession().refresh(account);
        List<Currency> currencyList = settingsManager.getCurrencyList();
        final AccountBalanceInfo accountBalanceInfo = new AccountBalanceInfo();
        ExecutorService executor = Executors.newCachedThreadPool();
        for(final Currency currency : currencyList) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        VirtualWallet wallet = accountManager.getVirtualWallet(account, currency);
                        BigDecimal balance = BigDecimal.ZERO;
                        String address = null;
                        if(wallet != null) {
                            balance = accountManager.getVirtualWalletBalance(wallet);
                            if(wallet.getCurrency().getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
                                List<Address> addressList = daemonManager.getAddressList(wallet);
                                if (!addressList.isEmpty()) {
                                    address = addressList.get(0).getAddress();
                                }
                            }
                        }
                        accountBalanceInfo.add(currency, balance, address);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            executor.execute(runnable);
        }
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        Collections.sort(accountBalanceInfo.getAccountBalances(), new Comparator<AccountBalanceInfo.AccountBalance>() {
            @Override
            public int compare(AccountBalanceInfo.AccountBalance o1, AccountBalanceInfo.AccountBalance o2) {
                return o1.getCurrency().getCurrencyName().compareTo(o2.getCurrency().getCurrencyName());
            }
        });
        return accountBalanceInfo;
    }

    @Override
    public Object[][] createHighChartsOHLCData(@NonNull List<Candle> candleList) throws Exception {
        int length = candleList.size();
        Object[][] result = new Object[length][6];
        for(int i = 0; i < length; i++) {
            Candle candle = candleList.get(length - i - 1);
            result[i][0] = candle.getOpenTime().getTime();
            result[i][1] = candle.getOpen();
            result[i][2] = candle.getHigh();
            result[i][3] = candle.getLow();
            result[i][4] = candle.getClose();
            result[i][5] = candle.getVolume();
        }
        return result;
    }
}
