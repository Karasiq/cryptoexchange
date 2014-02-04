package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AbstractAccountManager;
import com.springapp.cryptoexchange.database.AbstractDaemonManager;
import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.*;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping(value = "/rest/account.json", headers = "X-Ajax-Call=true")
@CommonsLog
@Secured("ROLE_USER")
public class AccountController {
    @Autowired
    AbstractConvertService convertService;

    @Autowired
    AbstractAccountManager accountManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractDaemonManager daemonManager;

    @Cacheable(value = "getAccountBalanceInfo", key = "#principal.name")
    @RequestMapping("/balance")
    @ResponseBody
    public ApiDefs.ApiStatus<AbstractConvertService.AccountBalanceInfo> getAccountBalanceInfo(Principal principal) {
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert account != null; // Shouldn't happen
            return new ApiDefs.ApiStatus<>(true, null, convertService.createAccountBalanceInfo(account));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }

    @Cacheable(value = "getAccountOrders", key = "#principal.name")
    @RequestMapping("/orders")
    @ResponseBody
    public ApiDefs.ApiStatus<List<Order>> getAccountOrdersInfo(Principal principal) {
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert account != null; // Shouldn't happen
            return new ApiDefs.ApiStatus<>(true, null, accountManager.getAccountOrders(account, 200));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }

    @Cacheable(value = "getAccountOrdersByPair", key = "#principal.name + #tradingPairId.toString()")
    @RequestMapping("/orders/{tradingPairId}")
    @ResponseBody
    public ApiDefs.ApiStatus<List<Order>> getAccountOrdersByPair(Principal principal, @PathVariable Long tradingPairId) {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert account != null; // Shouldn't happen
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
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert account != null; // Shouldn't happen
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
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert account != null; // Shouldn't happen
            return new ApiDefs.ApiStatus<>(true, null, historyManager.getAccountHistoryByPair(tradingPair, account, 200));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }

    @RequestMapping(value = "/address/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    public String generateDepositAddress(@PathVariable long currencyId, Principal principal) throws Exception {
        Currency currency = settingsManager.getCurrency(currencyId);
        if(!currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
            throw new IllegalArgumentException();
        }
        Account account = accountManager.getAccount(principal.getName());
        VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
        List<Address> addressList = daemonManager.getAddressList(virtualWallet);
        if(addressList.isEmpty()) {
            return daemonManager.createWalletAddress(virtualWallet);
        } else {
            return addressList.get(0).getAddress();
        }
    }
}
