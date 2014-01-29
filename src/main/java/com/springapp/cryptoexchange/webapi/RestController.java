package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractMarketManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/rest/api.json")
public class RestController {

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractMarketManager marketManager;

    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractConvertService convertService;

    @Cacheable("getTradingPairs")
    @RequestMapping(value = "/info")
    @ResponseBody
    List<TradingPair> getTradingPairs() {
        return settingsManager.getTradingPairs();
    }


    @Cacheable(value = "getMarketPrices", key = "#tradingPairId")
    @RequestMapping(value = "/info/{tradingPairId}")
    @ResponseBody
    TradingPair getMarketPrices(@PathVariable long tradingPairId) {
        return settingsManager.getTradingPair(tradingPairId);
    }

    @Cacheable(value = "getMarketHistory", key = "#tradingPairId")
    @RequestMapping("/history/{tradingPairId}")
    @ResponseBody
    List<AbstractConvertService.MarketHistory> getMarketHistory(@PathVariable long tradingPairId) {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        return convertService.createHistory(historyManager.getMarketHistory(tradingPair, 100));
    }

    @Cacheable(value = "getMarketDepth", key = "#tradingPairId")
    @RequestMapping("/depth/{tradingPairId}")
    @ResponseBody
    AbstractConvertService.Depth getMarketDepth(@PathVariable long tradingPairId) {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        return convertService.createDepth(marketManager.getOpenOrders(tradingPair, Order.Type.BUY, 100, false), marketManager.getOpenOrders(tradingPair, Order.Type.SELL, 100, true));
    }
}
