package com.springapp.cryptoexchange.webapi.master;

import com.bitcoin.daemon.Address;
import com.springapp.cryptoexchange.database.AccountManager;
import com.springapp.cryptoexchange.database.DaemonManager;
import com.springapp.cryptoexchange.database.SettingsManager;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;

@Controller
@RequestMapping("/rest/withdraw.json")
@CommonsLog
@Secured("ROLE_USER")
@Profile("master") // Main instance, cannot distribute
public class WithdrawControllerImpl implements WithdrawController {
    @Autowired
    AccountManager accountManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    DaemonManager daemonManager;

    @Caching(evict = {
            @CacheEvict(value = "getAccountBalances", key = "#principal.name"),
            @CacheEvict(value = "getTransactions", key = "#principal.name + #currencyId")
    })
    @Transactional
    @Override
    @RequestMapping(value = "/crypto/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("all")
    public Address.Transaction withdrawCrypto(@PathVariable long currencyId, @RequestParam String address, @RequestParam BigDecimal amount, Principal principal) throws Exception {
        try {
            Currency currency = settingsManager.getCurrency(currencyId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.isTrue(currency != null && account != null & currency.isEnabled() && account.isEnabled() && currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO), "Invalid parameters");
            VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
            Address.Transaction transaction = daemonManager.withdrawFunds(virtualWallet, address, amount);
            log.info(String.format("Withdraw success: %s %s => %s", amount, currency.getCurrencyCode(), address));
            return transaction;
        } catch (Exception e) {
            log.debug(e.getStackTrace());
            log.error(e);
            throw e;
        }
    }
}
