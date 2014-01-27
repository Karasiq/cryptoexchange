package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractMarketManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConvertService implements AbstractConvertService { // Cache/convert layer
    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractMarketManager marketManager;

    private volatile List<MarketHistory> cachedHistory = null;
    private volatile Depth cachedDepth = null;

    private final Object depthLock = new Object(), historyLock = new Object();

    @Async
    public void clearDepthCache() {
        synchronized (depthLock) {
            cachedDepth = null;
        }
    }

    @Async
    public void clearHistoryCache() {
        synchronized (historyLock) {
            cachedHistory = null;
        }
    }

    public Depth getMarketDepth(TradingPair tradingPair) {
        synchronized (depthLock) {
            if (cachedDepth == null) {
                cachedDepth = createDepth(marketManager.getOpenOrders(tradingPair, Order.Type.BUY, 100, false), marketManager.getOpenOrders(tradingPair, Order.Type.SELL, 100, true));
            }
            return cachedDepth;
        }
    }

    public List<MarketHistory> getMarketHistory(TradingPair tradingPair) {
        synchronized (historyLock) {
            if (cachedHistory == null) {
                cachedHistory = createHistory(historyManager.getMarketHistory(tradingPair, 100));
            }
            return cachedHistory;
        }
    }

    private Depth createDepth(List<Order> buyOrders, List<Order> sellOrders) {
        Depth depth = new Depth();
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
    private List<MarketHistory> createHistory(List<Order> orders) {
        List<MarketHistory> marketHistoryList = new ArrayList<>();
        for(Order order : orders) {
            marketHistoryList.add(new MarketHistory(order));
        }
        return marketHistoryList;
    }
}
