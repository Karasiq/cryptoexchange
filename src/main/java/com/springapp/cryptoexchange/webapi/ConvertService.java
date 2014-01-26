package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConvertService implements AbstractConvertService {
    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    public PriceInfo createPriceInfo(TradingPair tradingPair) {
        return new PriceInfo(tradingPair);
    }
    public Depth createDepth(List<Order> buyOrders, List<Order> sellOrders) {
        Depth depth = new Depth();
        Depth.DepthEntry depthEntry = new Depth.DepthEntry();
        for(Order order : buyOrders) {
            if(depthEntry.price != null && !depthEntry.price.equals(order.getPrice())) {
                depth.buyOrders.add(depthEntry);
                depthEntry = new Depth.DepthEntry();
            }
            depthEntry.addOrder(order);
        }
        depth.buyOrders.add(depthEntry);
        depthEntry = new Depth.DepthEntry();

        for(Order order : sellOrders) {
            if(depthEntry.price != null && !depthEntry.price.equals(order.getPrice())) {
                depth.sellOrders.add(depthEntry);
                depthEntry = new Depth.DepthEntry();
            }
            depthEntry.addOrder(order);
        }
        depth.sellOrders.add(depthEntry);
        return depth;
    }
    public List<MarketHistory> createHistory(List<Order> orders) {
        List<MarketHistory> marketHistoryList = new ArrayList<>();
        for(Order order : orders) {
            marketHistoryList.add(new MarketHistory(order));
        }
        return marketHistoryList;
    }
}
