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

import java.math.BigDecimal;
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
    public ApiDefs.ApiStatus<String> generateDepositAddress(@PathVariable long currencyId, Principal principal) throws Exception {
        try {
            Currency currency = settingsManager.getCurrency(currencyId);
            Account account = accountManager.getAccount(principal.getName());
            VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
            List<Address> addressList = daemonManager.getAddressList(virtualWallet);
            String address;
            if(addressList.isEmpty()) {
                address = daemonManager.createWalletAddress(virtualWallet);
            } else {
                address = addressList.get(0).getAddress();
            }
            return new ApiDefs.ApiStatus<>(true, null, address);
        } catch (Exception e) {
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getLocalizedMessage(), null);
        }
    }

    @RequestMapping(value = "/withdraw/crypto/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ApiDefs.ApiStatus withdrawCrypto(@PathVariable long currencyId, @RequestParam String address, @RequestParam BigDecimal amount, Principal principal) {
        Currency currency = settingsManager.getCurrency(currencyId);
        Account account = accountManager.getAccount(principal.getName());
        try {
            VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
            if (currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
                daemonManager.withdrawFunds(virtualWallet, address, amount);
                return new ApiDefs.ApiStatus(true, null, null);
            }
        } catch (Exception e) {
            log.error(e);
            return new ApiDefs.ApiStatus(false, e.getLocalizedMessage(), null);
        }
        return new ApiDefs.ApiStatus(false, "Unknown error", null);
    }
}
