package com.springapp.cryptoexchange.webapi.data;

import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractMarketManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.utils.AbstractConvertService;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/rest/api.json")
@CommonsLog
@Profile("data")
public class PublicController {

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
    public List<TradingPair> getTradingPairs() {
        return settingsManager.getTradingPairs();
    }


    @Cacheable(value = "getMarketPrices", key = "#tradingPairId")
    @RequestMapping(value = "/info/{tradingPairId}")
    @ResponseBody
    public TradingPair getMarketPrices(@PathVariable long tradingPairId) {
        return settingsManager.getTradingPair(tradingPairId);
    }

    @Cacheable(value = "getMarketHistory", key = "#tradingPairId")
    @RequestMapping("/history/{tradingPairId}")
    @ResponseBody
    public List<AbstractConvertService.MarketHistory> getMarketHistory(@PathVariable long tradingPairId) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        assert tradingPair != null && tradingPair.isEnabled();
        return convertService.createHistory(historyManager.getMarketHistory(tradingPair, 100));
    }

    @Cacheable(value = "getMarketChartData", key = "#tradingPairId")
    @RequestMapping("/chart/{tradingPairId}")
    @ResponseBody
    public Object[][] getMarketChartData(@PathVariable long tradingPairId) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        assert tradingPair != null && tradingPair.isEnabled();
        return convertService.createHighChartsOHLCData(historyManager.getMarketChartData(tradingPair, 100));
    }

    @Cacheable(value = "getMarketDepth", key = "#tradingPairId")
    @RequestMapping("/depth/{tradingPairId}")
    @ResponseBody
    public AbstractConvertService.Depth getMarketDepth(@PathVariable long tradingPairId) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        assert tradingPair != null && tradingPair.isEnabled();
        return convertService.createDepth(marketManager.getOpenOrders(tradingPair, Order.Type.BUY, 100), marketManager.getOpenOrders(tradingPair, Order.Type.SELL, 100));
    }


}
