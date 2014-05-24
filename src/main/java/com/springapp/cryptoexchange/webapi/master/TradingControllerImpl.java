package com.springapp.cryptoexchange.webapi.master;

import com.springapp.cryptoexchange.database.AccountManager;
import com.springapp.cryptoexchange.database.MarketManager;
import com.springapp.cryptoexchange.database.SettingsManager;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;

@RestController
@CommonsLog
@Secured("ROLE_USER")
@RequestMapping("/rest/trade.json")
@Profile("master")
public class TradingControllerImpl implements TradingController {
    @Autowired
    SettingsManager settingsManager;

    @Autowired
    MarketManager marketManager;

    @Autowired
    AccountManager accountManager;

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    @Override
    @RequestMapping(value = "/order/create/{tradingPairId}", method = RequestMethod.POST)
    @SuppressWarnings("all")
    public long createOrder(@PathVariable long tradingPairId, @RequestParam Order.Type type, @RequestParam BigDecimal price, @RequestParam BigDecimal amount, Principal principal) throws Exception {
        try {
            TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.isTrue(account != null && tradingPair != null && account.isEnabled() && tradingPair.isEnabled(), "Invalid parameters");
            VirtualWallet sourceWallet = accountManager.getVirtualWallet(account, type.equals(Order.Type.SELL) ? tradingPair.getFirstCurrency() : tradingPair.getSecondCurrency()), destWallet = accountManager.getVirtualWallet(account, type.equals(Order.Type.SELL) ? tradingPair.getSecondCurrency() : tradingPair.getFirstCurrency());
            return marketManager.executeOrder(new Order(type, amount, price, tradingPair, sourceWallet, destWallet, account)).getId();
        } catch (Exception e) {
            log.debug(e.getStackTrace());
            log.error(e);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    @Override
    @RequestMapping(value = "/order/{orderId}/cancel", method = RequestMethod.POST)
    @SuppressWarnings("unchecked")
    public void cancelOrder(@PathVariable long orderId, Principal principal) throws Exception {
        try {
            Order order = marketManager.getOrder(orderId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.isTrue(order != null && account != null && order.getAccount().equals(account) && order.isActual(), "Invalid parameters");
            marketManager.cancelOrder(order);
        } catch (Exception e) {
            log.debug(e.getStackTrace());
            log.error(e);
            throw e;
        }
    }
}
