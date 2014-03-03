package com.springapp.cryptoexchange.webapi.data;

import com.springapp.cryptoexchange.database.*;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.utils.ConvertService;
import com.springapp.cryptoexchange.webapi.ApiDefs;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public ApiDefs.ApiStatus<ConvertService.AccountBalanceInfo> getAccountBalances(Principal principal) {
        try {
            Account account = accountManager.getAccount(principal.getName());
            Assert.notNull(account);
            return new ApiDefs.ApiStatus<>(true, null, convertService.createAccountBalanceInfo(account));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }

    @RequestMapping(value = "/order/{orderId}", method = RequestMethod.GET)
    @ResponseBody
    @SuppressWarnings("all")
    public ApiDefs.ApiStatus<Order> getOrderStatus(@PathVariable long orderId, Principal principal) {
        try {
            Order order = marketManager.getOrder(orderId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.isTrue(order != null && account != null && account.isEnabled(), "Invalid parameters");
            Assert.isTrue(order.getAccount().equals(account), "This is not your order");
            return new ApiDefs.ApiStatus<>(true, null, order);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getLocalizedMessage(), null);
        }
    }

    @Cacheable(value = "getAccountOrders", key = "#principal.name")
    @RequestMapping("/orders")
    @ResponseBody
    public ApiDefs.ApiStatus<List<Order>> getAccountOrdersInfo(Principal principal) {
        try {
            Account account = accountManager.getAccount(principal.getName());
            Assert.notNull(account);
            return new ApiDefs.ApiStatus<>(true, null, accountManager.getAccountOrders(account, 200));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }

    @Cacheable(value = "getAccountOrdersByPair", key = "#principal.name + #tradingPairId")
    @RequestMapping("/orders/{tradingPairId}")
    @ResponseBody
    public ApiDefs.ApiStatus<List<Order>> getAccountOrdersByPair(Principal principal, @PathVariable long tradingPairId) {
        try {
            TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.notNull(tradingPair);
            Assert.notNull(account);
            return new ApiDefs.ApiStatus<>(true, null, accountManager.getAccountOrdersByPair(tradingPair, account, 200));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }

    @Cacheable(value = "getAccountHistory", key = "#principal.name")
    @RequestMapping("/history")
    @ResponseBody
    public ApiDefs.ApiStatus<List<Order>> getAccountHistory(Principal principal) {
        try {
            Account account = accountManager.getAccount(principal.getName());
            Assert.notNull(account);
            return new ApiDefs.ApiStatus<>(true, null, historyManager.getAccountHistory(account, 200));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }

    @Cacheable(value = "getAccountHistoryByPair", key = "#principal.name + #tradingPairId.toString()")
    @RequestMapping("/history/{tradingPairId}")
    @ResponseBody
    public ApiDefs.ApiStatus<List<Order>> getAccountHistoryByPair(Principal principal, @PathVariable Long tradingPairId) {
        try {
            TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.notNull(tradingPair);
            Assert.notNull(account);
            return new ApiDefs.ApiStatus<>(true, null, historyManager.getAccountHistoryByPair(tradingPair, account, 200));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }

    @Cacheable(value = "getTransactions", key = "#principal.getName() + #currencyId")
    @RequestMapping(value = "/transactions/{currencyId}")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ApiDefs.ApiStatus<List<com.bitcoin.daemon.Address.Transaction>> getTransactions(@PathVariable long currencyId, Principal principal) {
        try {
            Currency currency = settingsManager.getCurrency(currencyId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.isTrue(currency != null && account != null & currency.isEnabled() && account.isEnabled(), "Invalid parameters");
            Set<Object> addressSet = new HashSet<>();
            List<Address> addressList = daemonManager.getAddressList(accountManager.getVirtualWallet(account, currency));
            for(Address address : addressList) {
                addressSet.add(address.getAddress());
            }
            return new ApiDefs.ApiStatus(true, null, daemonManager.getAccount(currency).getTransactions(addressSet));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus(false, e.getLocalizedMessage(), null);
        }
    }

    @Caching(evict = {
            @CacheEvict(value = "getAccountBalances", key = "#principal.name")
    })
    @RequestMapping(value = "/address/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    public ApiDefs.ApiStatus<String> generateDepositAddress(@PathVariable long currencyId, Principal principal) throws Exception {
        try {
            Currency currency = settingsManager.getCurrency(currencyId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.isTrue(currency != null && account != null & currency.isEnabled() && account.isEnabled(), "Invalid parameters");
            VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
            List<Address> addressList = daemonManager.getAddressList(virtualWallet);
            String address;
            if(addressList == null || addressList.isEmpty()) {
                address = daemonManager.createWalletAddress(virtualWallet);
                log.info("Address generated: " + address);
            } else {
                address = addressList.get(0).getAddress();
            }
            return new ApiDefs.ApiStatus<>(true, null, address);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getLocalizedMessage(), null);
        }
    }
}
