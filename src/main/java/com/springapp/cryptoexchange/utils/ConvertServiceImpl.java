package com.springapp.cryptoexchange.utils;

import com.springapp.cryptoexchange.database.*;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.database.model.Currency;
import lombok.NonNull;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    public Depth createDepth(@NonNull List<Order> buyOrders, @NonNull List<Order> sellOrders) throws Exception {
        final Depth depth = new Depth();
        Depth.Entry depthEntry = new Depth.Entry();
        if(buyOrders != null && !buyOrders.isEmpty()) {
            for(Order order : buyOrders) {
                if(depthEntry.price != null && !depthEntry.price.equals(order.getPrice())) {
                    depth.buyOrders.add(depthEntry);
                    depthEntry = new Depth.Entry();
                }
                depthEntry.addOrder(order);
            }
            depth.buyOrders.add(depthEntry);
            depthEntry = new Depth.Entry();
        }

        if(sellOrders != null && !sellOrders.isEmpty()) {
            for(Order order : sellOrders) {
                if(depthEntry.price != null && !depthEntry.price.equals(order.getPrice())) {
                    depth.sellOrders.add(depthEntry);
                    depthEntry = new Depth.Entry();
                }
                depthEntry.addOrder(order);
            }
            depth.sellOrders.add(depthEntry);
        }
        return depth;
    }
    public List<MarketHistory> createHistory(@NonNull List<Order> orders) throws Exception {
        List<MarketHistory> marketHistoryList = new ArrayList<>(orders.size());
        for(Order order : orders) {
            marketHistoryList.add(new MarketHistory(order));
        }
        return marketHistoryList;
    }

    @Transactional
    public AccountBalanceInfo createAccountBalanceInfo(final @NonNull Account account) throws Exception {
        sessionFactory.getCurrentSession().refresh(account);
        List<Currency> currencyList = settingsManager.getCurrencyList();
        final AccountBalanceInfo accountBalanceInfo = new AccountBalanceInfo();
        ExecutorService executor = Executors.newFixedThreadPool(10);
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
