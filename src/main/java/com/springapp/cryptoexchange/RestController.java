package com.springapp.cryptoexchange;

import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractMarketManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.webapi.AbstractConvertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/rest-api")
public class RestController {
    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractMarketManager marketManager;

    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractConvertService convertService;

    @RequestMapping(value = "/info/{tradingPairId}")
    @ResponseBody
    AbstractConvertService.PriceInfo getPrices(@PathVariable long tradingPairId) {
        return convertService.createPriceInfo(settingsManager.getTradingPair(tradingPairId));
    }

    @RequestMapping("/history/{tradingPairId}")
    @ResponseBody
    List<AbstractConvertService.MarketHistory> getMarketHistory(@PathVariable long tradingPairId) {
        List<Order> history = historyManager.getMarketHistory(settingsManager.getTradingPair(tradingPairId), 50);
        return convertService.createHistory(history);
    }

    @RequestMapping("/depth/{tradingPairId}")
    @ResponseBody
    AbstractConvertService.Depth getMarketDepth(@PathVariable long tradingPairId) {
        List<Order> buyOrders = marketManager.getOpenOrders(settingsManager.getTradingPair(tradingPairId), Order.Type.BUY, 100, false), sellOrders = marketManager.getOpenOrders(settingsManager.getTradingPair(tradingPairId), Order.Type.SELL, 100, true);
        return convertService.createDepth(buyOrders, sellOrders);
    }
}
