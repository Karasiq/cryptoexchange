package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractMarketManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @RequestMapping(value = "/info")
    @ResponseBody
    List<TradingPair> getTradingPairs() {
        return settingsManager.getTradingPairs();
    }

    @RequestMapping(value = "/info/{tradingPairId}")
    @ResponseBody
    TradingPair getPrices(@PathVariable long tradingPairId) {
        return settingsManager.getTradingPair(tradingPairId);
    }

    @RequestMapping("/history/{tradingPairId}")
    @ResponseBody
    List<AbstractConvertService.MarketHistory> getMarketHistory(@PathVariable long tradingPairId) {
        return convertService.getMarketHistory(settingsManager.getTradingPair(tradingPairId));
    }

    @RequestMapping("/depth/{tradingPairId}")
    @ResponseBody
    AbstractConvertService.Depth getMarketDepth(@PathVariable long tradingPairId) {
        return convertService.getMarketDepth(settingsManager.getTradingPair(tradingPairId));
    }
}
