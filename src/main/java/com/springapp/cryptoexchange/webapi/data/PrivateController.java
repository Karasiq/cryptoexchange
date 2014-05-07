package com.springapp.cryptoexchange.webapi.data;

import com.springapp.cryptoexchange.database.*;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.utils.ConvertService;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping(value = "/rest/account.json", headers = "X-Ajax-Call=true")
@CommonsLog
@Secured("ROLE_USER")
@Profile("data")
public class PrivateController {
    @Autowired
    ConvertService convertService;

    @Autowired
    AccountManager accountManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    HistoryManager historyManager;

    @Autowired
    DaemonManager daemonManager;

    @Autowired
    MarketManager marketManager;

    @Cacheable(value = "getAccountBalances", key = "#principal.name")
    @RequestMapping("/balance")
    @ResponseBody
    public ConvertService.AccountBalanceInfo getAccountBalances(Principal principal) throws Exception {
        Account account = accountManager.getAccount(principal.getName());
        Assert.notNull(account);
        return convertService.createAccountBalanceInfo(account);
    }

    @RequestMapping(value = "/order/{orderId}", method = RequestMethod.GET)
    @ResponseBody
    @SuppressWarnings("all")
    public Order getOrderStatus(@PathVariable long orderId, Principal principal) {
        Order order = marketManager.getOrder(orderId);
        Account account = accountManager.getAccount(principal.getName());
        Assert.isTrue(order != null && account != null && account.isEnabled(), "Invalid parameters");
        Assert.isTrue(order.getAccount().equals(account), "This is not your order");
        return order;
    }

    @Cacheable(value = "getAccountOrders", key = "#principal.name")
    @RequestMapping("/orders")
    @ResponseBody
    public List<Order> getAccountOrdersInfo(Principal principal) {
        Account account = accountManager.getAccount(principal.getName());
        Assert.notNull(account);
        return accountManager.getAccountOrders(account, 50);
    }

    @Cacheable(value = "getAccountOrdersByPair", key = "#principal.name + '/' + #tradingPairId")
    @RequestMapping("/orders/{tradingPairId}")
    @ResponseBody
    public List<Order> getAccountOrdersByPair(Principal principal, @PathVariable long tradingPairId) {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Account account = accountManager.getAccount(principal.getName());
        Assert.notNull(tradingPair);
        Assert.notNull(account);
        return accountManager.getAccountOrdersByPair(tradingPair, account, 20);
    }

    @Cacheable(value = "getAccountHistory", key = "#principal.name")
    @RequestMapping("/history")
    @ResponseBody
    public List<Order> getAccountHistory(Principal principal) {
        Account account = accountManager.getAccount(principal.getName());
        Assert.notNull(account);
        return historyManager.getAccountHistory(account, 20);
    }

    @Cacheable(value = "getAccountHistoryByPair", key = "#principal.name + '/' + #tradingPairId")
    @RequestMapping("/history/{tradingPairId}")
    @ResponseBody
    public List<Order> getAccountHistoryByPair(Principal principal, @PathVariable long tradingPairId) {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Account account = accountManager.getAccount(principal.getName());
        Assert.notNull(tradingPair);
        Assert.notNull(account);
        return historyManager.getAccountHistoryByPair(tradingPair, account, 20);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "getTransactions", key = "#principal.name + '/' + #currencyId")
    @RequestMapping(value = "/transactions/{currencyId}")
    @ResponseBody
    @SuppressWarnings("all")
    public List<com.bitcoin.daemon.Address.Transaction> getTransactions(@PathVariable long currencyId, Principal principal) throws Exception {
        Currency currency = settingsManager.getCurrency(currencyId);
        Account account = accountManager.getAccount(principal.getName());
        Assert.isTrue(currency != null && account != null & account.isEnabled(), "Invalid parameters");
        Assert.isTrue(currency.isEnabled(), "Currency disabled");
        final List<com.bitcoin.daemon.Address.Transaction> transactionList = daemonManager.getWalletTransactions(accountManager.getVirtualWallet(account, currency));
        Collections.sort(transactionList);
        return transactionList;
    }

    @Caching(evict = {
            @CacheEvict(value = "getAccountBalances", key = "#principal.name")
    })
    @RequestMapping(value = "/address/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("all")
    public String generateDepositAddress(@PathVariable long currencyId, Principal principal) throws Exception {
        Currency currency = settingsManager.getCurrency(currencyId);
        Account account = accountManager.getAccount(principal.getName());
        Assert.isTrue(currency != null && account != null && account.isEnabled(), "Invalid parameters");
        Assert.isTrue(currency.isEnabled(), "Currency disabled");
        VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
        List<Address> addressList = daemonManager.getAddressList(virtualWallet);
        String address;
        if(addressList == null || addressList.isEmpty()) {
            address = daemonManager.createWalletAddress(virtualWallet);
            log.info("Address generated: " + address);
        } else {
            address = addressList.get(0).getAddress();
        }
        return address;
    }
}
