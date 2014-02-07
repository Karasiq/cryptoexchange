package com.springapp.cryptoexchange.webapi;

import com.bitcoin.daemon.CryptoCoinWallet;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Cacheable(value = "getAccountBalances", key = "#principal.name")
    @RequestMapping("/balance")
    @ResponseBody
    public ApiDefs.ApiStatus<AbstractConvertService.AccountBalanceInfo> getAccountBalances(Principal principal) {
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

    @Cacheable(value = "getAccountOrdersByPair", key = "#principal.name + #tradingPairId")
    @RequestMapping("/orders/{tradingPairId}")
    @ResponseBody
    public ApiDefs.ApiStatus<List<Order>> getAccountOrdersByPair(Principal principal, @PathVariable long tradingPairId) {
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

    @Cacheable(value = "getTransactions", key = "#principal.getName() + #currencyId")
    @RequestMapping(value = "/transactions/{currencyId}")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ApiDefs.ApiStatus<List<com.bitcoin.daemon.Address.Transaction>> getTransactions(@PathVariable long currencyId, Principal principal) {
        Currency currency = settingsManager.getCurrency(currencyId);
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert currency != null && account != null & currency.isEnabled() && account.isEnabled();
            Set<Object> addressSet = new HashSet<>();
            List<Address> addressList = daemonManager.getAddressList(accountManager.getVirtualWallet(account, currency));
            for(Address address : addressList) {
                addressSet.add(address.getAddress());
            }
            return new ApiDefs.ApiStatus(true, null, ((CryptoCoinWallet.Account) daemonManager.getAccount(currency)).getTransactions(addressSet));
        } catch (Exception e) {
            log.error(e);
            return new ApiDefs.ApiStatus(false, e.getLocalizedMessage(), null);
        }
    }

    @RequestMapping(value = "/address/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    public ApiDefs.ApiStatus<String> generateDepositAddress(@PathVariable long currencyId, Principal principal) throws Exception {
        Currency currency = settingsManager.getCurrency(currencyId);
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert currency != null && account != null & currency.isEnabled() && account.isEnabled();
            VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
            List<Address> addressList = daemonManager.getAddressList(virtualWallet);
            String address;
            if(addressList.isEmpty()) {
                address = daemonManager.createWalletAddress(virtualWallet);
            } else {
                address = addressList.get(0).getAddress();
            }
            log.info("Address generated: " + address);
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
            assert currency != null && account != null & currency.isEnabled() && account.isEnabled();
            VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
            if (currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
                daemonManager.withdrawFunds(virtualWallet, address, amount);
                log.info(String.format("Withdraw success: %s %s => %s", amount, currency.getCurrencyCode(), address));
                return new ApiDefs.ApiStatus(true, null, null);
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            log.error(e);
            return new ApiDefs.ApiStatus(false, e.getLocalizedMessage(), null);
        }
    }
}
