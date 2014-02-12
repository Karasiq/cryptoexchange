package com.springapp.cryptoexchange.webapi.master;

import com.springapp.cryptoexchange.database.AbstractAccountManager;
import com.springapp.cryptoexchange.database.AbstractDaemonManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Currency;
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
@RequestMapping("/rest/withdraw.json")
@CommonsLog
@Secured("ROLE_USER")
@Profile("master") // Main instance, cannot distribute
public class WithdrawControllerImpl implements WithdrawController {
    @Autowired
    AbstractAccountManager accountManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractDaemonManager daemonManager;

    @Override
    @RequestMapping(value = "/crypto/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ApiDefs.ApiStatus withdrawCrypto(@PathVariable long currencyId, @RequestParam String address, @RequestParam BigDecimal amount, Principal principal) {
        try {
            Currency currency = settingsManager.getCurrency(currencyId);
            Account account = accountManager.getAccount(principal.getName());
            Assert.isTrue(currency != null && account != null & currency.isEnabled() && account.isEnabled(), "Invalid parameters");
            VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
            if (currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
                daemonManager.withdrawFunds(virtualWallet, address, amount);
                WithdrawControllerImpl.log.info(String.format("Withdraw success: %s %s => %s", amount, currency.getCurrencyCode(), address));
                return new ApiDefs.ApiStatus(true, null, null);
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            WithdrawControllerImpl.log.error(e);
            return new ApiDefs.ApiStatus(false, e.getLocalizedMessage(), null);
        }
    }
}
