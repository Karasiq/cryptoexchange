package com.springapp.cryptoexchange.webapi.data;

import com.springapp.cryptoexchange.database.HistoryManager;
import com.springapp.cryptoexchange.database.MarketManager;
import com.springapp.cryptoexchange.database.NewsManager;
import com.springapp.cryptoexchange.database.SettingsManager;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.News;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.utils.ConvertService;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RestController
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

    @Autowired
    NewsManager newsManager;

    @Cacheable("getCurrencies")
    @RequestMapping(value = "/currencies")
    public List<Currency> getCurrencies() {
        List<Currency> currencyList = settingsManager.getCurrencyList();
        Collections.sort(currencyList, new Comparator<Currency>() {
            @Override
            public int compare(Currency o1, Currency o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return currencyList;
    }

    @Cacheable(value = "getCurrencyInfo", key = "#currencyId")
    @RequestMapping(value = "/currency/{currencyId}")
    public Currency getCurrencyInfo(@PathVariable long currencyId) {
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        return currency;
    }

    @Cacheable("getTradingPairs")
    @RequestMapping(value = "/trading_pairs")
    public List<TradingPair> getTradingPairs() {
        List<TradingPair> tradingPairList = settingsManager.getTradingPairs();
        Collections.sort(tradingPairList, new Comparator<TradingPair>() {
            @Override
            public int compare(TradingPair o1, TradingPair o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return tradingPairList;
    }


    @Cacheable(value = "getTradingPairInfo", key = "#tradingPairId")
    @RequestMapping(value = "/trading_pair/{tradingPairId}")
    public TradingPair getTradingPairInfo(@PathVariable long tradingPairId) {
        return settingsManager.getTradingPair(tradingPairId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "getMarketHistory", key = "#tradingPairId")
    @RequestMapping("/history/{tradingPairId}")
    public List<ConvertService.MarketHistory> getMarketHistory(@PathVariable long tradingPairId) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Assert.isTrue(tradingPair != null && tradingPair.isEnabled(), "Invalid pair");
        return convertService.createHistory(historyManager.getMarketHistory(tradingPair).setMaxResults(50));
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    @Cacheable(value = "getMarketChartData", key = "#tradingPairId")
    @RequestMapping("/chart/{tradingPairId}")
    public Object[][] getMarketChartData(@PathVariable long tradingPairId) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Assert.isTrue(tradingPair != null && tradingPair.isEnabled(), "Invalid pair");
        return convertService.createHighChartsOHLCData(historyManager.getMarketChartData(tradingPair).setMaxResults(100).list());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "getMarketDepth", key = "#tradingPairId")
    @RequestMapping("/depth/{tradingPairId}")
    public ConvertService.Depth getMarketDepth(@PathVariable long tradingPairId) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Assert.isTrue(tradingPair != null && tradingPair.isEnabled(), "Invalid pair");
        return convertService.createDepth(tradingPair, 20);
    }


    @Cacheable("getNews")
    @RequestMapping(value = "/news")
    public List<News> getNews() {
        return newsManager.getNews(20);
    }
}
