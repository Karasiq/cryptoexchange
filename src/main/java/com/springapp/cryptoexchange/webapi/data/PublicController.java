package com.springapp.cryptoexchange.webapi.data;

import com.springapp.cryptoexchange.database.HistoryManager;
import com.springapp.cryptoexchange.database.MarketManager;
import com.springapp.cryptoexchange.database.SettingsManager;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.utils.ConvertService;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
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
    SettingsManager settingsManager;

    @Autowired
    MarketManager marketManager;

    @Autowired
    HistoryManager historyManager;

    @Autowired
    ConvertService convertService;

    @Cacheable("getCurrencies")
    @RequestMapping(value = "/currency")
    @ResponseBody
    public List<Currency> getCurrencies() {
        return settingsManager.getCurrencyList();
    }

    @Cacheable("getCurrencyInfo")
    @RequestMapping(value = "/currency/{currencyId}")
    @ResponseBody
    public Currency getCurrencyInfo(@PathVariable long currencyId) {
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        return currency;
    }

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
    public List<ConvertService.MarketHistory> getMarketHistory(@PathVariable long tradingPairId) throws Exception {
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
    public ConvertService.Depth getMarketDepth(@PathVariable long tradingPairId) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        assert tradingPair != null && tradingPair.isEnabled();
        return convertService.createDepth(marketManager.getOpenOrders(tradingPair, Order.Type.BUY, 100), marketManager.getOpenOrders(tradingPair, Order.Type.SELL, 100));
    }


}
