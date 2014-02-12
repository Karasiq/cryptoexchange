package com.springapp.cryptoexchange.webapi.master;

import com.springapp.cryptoexchange.database.AbstractAccountManager;
import com.springapp.cryptoexchange.database.AbstractMarketManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import com.springapp.cryptoexchange.webapi.ApiDefs;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;

@Controller
@CommonsLog
@Secured("ROLE_USER")
@RequestMapping("/rest/trade.json")
@Profile("master") // Main instance, cannot distribute
public class TradingControllerImpl implements TradingController {
    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractMarketManager marketManager;

    @Autowired
    AbstractAccountManager accountManager;

    @Override
    @RequestMapping(value = "/order/create/{tradingPairId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("all")
    public ApiDefs.ApiStatus<Long> createOrder(@PathVariable long tradingPairId, @RequestParam Order.Type type, @RequestParam BigDecimal price, @RequestParam BigDecimal amount, Principal principal) {
        try {
            TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.isTrue(account != null && tradingPair != null && account.isEnabled() && tradingPair.isEnabled(), "Invalid parameters");
            VirtualWallet sourceWallet = accountManager.getVirtualWallet(account, type.equals(Order.Type.SELL) ? tradingPair.getFirstCurrency() : tradingPair.getSecondCurrency()), destWallet = accountManager.getVirtualWallet(account, type.equals(Order.Type.SELL) ? tradingPair.getSecondCurrency() : tradingPair.getFirstCurrency());
            return new ApiDefs.ApiStatus<>(true, null, marketManager.executeOrder(new Order(type, amount, price, tradingPair, sourceWallet, destWallet, account)).getId());
        } catch (Exception e) {
            e.printStackTrace();
            TradingControllerImpl.log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getLocalizedMessage(), null);
        }
    }

    @Override
    @RequestMapping(value = "/order/{orderId}/cancel", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ApiDefs.ApiStatus cancelOrder(@PathVariable long orderId, Principal principal) {
        try {
            Order order = marketManager.getOrder(orderId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.isTrue(order != null && account != null && order.getAccount().equals(account) && order.isActual(), "Invalid parameters");
            marketManager.cancelOrder(order);
            return new ApiDefs.ApiStatus(true, null, null);
        } catch (Exception e) {
            e.printStackTrace();
            TradingControllerImpl.log.error(e);
            return new ApiDefs.ApiStatus(false, e.getLocalizedMessage(), null);
        }
    }
}
