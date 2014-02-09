package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.*;
import com.springapp.cryptoexchange.database.model.*;
import lombok.NonNull;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ConvertService implements AbstractConvertService { // Convert layer
    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractMarketManager marketManager;

    @Autowired
    AbstractDaemonManager daemonManager;

    @Autowired
    AbstractAccountManager accountManager;

    @Autowired
    SessionFactory sessionFactory;

    public Depth createDepth(List<Order> buyOrders, List<Order> sellOrders) throws Exception {
        final Depth depth = new Depth();
        Depth.DepthEntry depthEntry = new Depth.DepthEntry();
        if(buyOrders != null && !buyOrders.isEmpty()) {
            for(Order order : buyOrders) {
                if(depthEntry.price != null && !depthEntry.price.equals(order.getPrice())) {
                    depth.buyOrders.add(depthEntry);
                    depthEntry = new Depth.DepthEntry();
                }
                depthEntry.addOrder(order);
            }
            depth.buyOrders.add(depthEntry);
            depthEntry = new Depth.DepthEntry();
        }

        if(sellOrders != null && !sellOrders.isEmpty()) {
            for(Order order : sellOrders) {
                if(depthEntry.price != null && !depthEntry.price.equals(order.getPrice())) {
                    depth.sellOrders.add(depthEntry);
                    depthEntry = new Depth.DepthEntry();
                }
                depthEntry.addOrder(order);
            }
            depth.sellOrders.add(depthEntry);
        }
        return depth;
    }
    public List<MarketHistory> createHistory(List<Order> orders) throws Exception {
        List<MarketHistory> marketHistoryList = new ArrayList<>(orders.size());
        for(Order order : orders) {
            marketHistoryList.add(new MarketHistory(order));
        }
        return marketHistoryList;
    }

    @Transactional
    public AccountBalanceInfo createAccountBalanceInfo(Account account) throws Exception {
        sessionFactory.getCurrentSession().refresh(account);
        List<Currency> currencyList = settingsManager.getCurrencyList();
        AccountBalanceInfo accountBalanceInfo = new AccountBalanceInfo();
        for(Currency currency : currencyList) {
            VirtualWallet wallet = account.getBalance(currency);
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
        }
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
