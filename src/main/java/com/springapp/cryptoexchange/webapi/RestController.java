package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AbstractAccountManager;
import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractMarketManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/rest/api.json")
@CommonsLog
public class RestController {

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractMarketManager marketManager;

    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractConvertService convertService;

    @Autowired
    AbstractAccountManager accountManager;

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
        return convertService.createDepth(marketManager.getOpenOrders(tradingPair, Order.Type.BUY, 100, false), marketManager.getOpenOrders(tradingPair, Order.Type.SELL, 100, true));
    }

    @RequestMapping(value = "/order/create/{tradingPairId}", method = RequestMethod.POST)
    @ResponseBody
    @Secured("ROLE_USER")
    public ApiDefs.ApiStatus<Long> createOrder(@PathVariable long tradingPairId, @RequestParam Order.Type type, @RequestParam BigDecimal price, @RequestParam BigDecimal amount, Principal principal) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert account != null && tradingPair != null && account.isEnabled() && tradingPair.isEnabled();
            VirtualWallet sourceWallet = accountManager.getVirtualWallet(account, type.equals(Order.Type.SELL) ? tradingPair.getFirstCurrency() : tradingPair.getSecondCurrency()), destWallet = accountManager.getVirtualWallet(account, type.equals(Order.Type.SELL) ? tradingPair.getSecondCurrency() : tradingPair.getFirstCurrency());
            return new ApiDefs.ApiStatus<>(true, null, marketManager.executeOrder(new Order(type, amount, price, tradingPair, sourceWallet, destWallet, account)).getId());
        } catch (Exception e) {
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getLocalizedMessage(), null);
        }
    }

    @RequestMapping(value = "/order/cancel", method = RequestMethod.POST)
    @ResponseBody
    @Secured("ROLE_USER")
    @SuppressWarnings("unchecked")
    public ApiDefs.ApiStatus cancelOrder(@RequestParam long orderId, Principal principal) {
        Order order = marketManager.getOrder(orderId);
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert order != null && account != null && order.getAccount().equals(account) && order.isActual();
            marketManager.cancelOrder(order);
            return new ApiDefs.ApiStatus(true, null, null);
        } catch (Exception e) {
            log.error(e);
            return new ApiDefs.ApiStatus(false, e.getLocalizedMessage(), null);
        }
    }
}
