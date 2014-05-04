package com.springapp.cryptoexchange.webapi.master;

import com.bitcoin.daemon.Address;
import com.springapp.cryptoexchange.database.AccountManager;
import com.springapp.cryptoexchange.database.DaemonManager;
import com.springapp.cryptoexchange.database.SettingsManager;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/rest/withdraw.json")
@CommonsLog
@Secured("ROLE_USER")
@Profile("master")
public class WithdrawControllerImpl implements WithdrawController {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AccountManager accountManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    DaemonManager daemonManager;

    @SuppressWarnings("all")
    private void assertCanWithdraw(@NonNull Currency currency, @NonNull Account account, Currency.Type type) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        Assert.isTrue(currency.isEnabled() && account.isEnabled()
                && currency.getType().equals(type), "Invalid parameters");
        Assert.notNull(accountManager.getVirtualWallet(account, currency));

        List<VirtualWallet> virtualWalletList = session.createCriteria(VirtualWallet.class)
                .add(Restrictions.eq("account", account))
                .list();
        for(VirtualWallet virtualWallet : virtualWalletList) {
            Assert.isTrue(accountManager.getVirtualWalletBalance(virtualWallet).compareTo(BigDecimal.ZERO) >= 0, "One of your balances is negative");
        }
    }

    @Caching(evict = {
            @CacheEvict(value = "getAccountBalances", key = "#principal.name"),
            @CacheEvict(value = "getTransactions", key = "#principal.name + '/' + #currencyId")
    })
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    @Override
    @RequestMapping(value = "/crypto/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("all")
    public Address.Transaction withdrawCrypto(@PathVariable long currencyId, @RequestParam String address, @RequestParam BigDecimal amount, Principal principal) throws Exception {
        Assert.hasLength(address, "Invalid address");
        Assert.isTrue(amount != null && amount.compareTo(BigDecimal.ZERO) > 0, "Invalid amount");
        Currency currency = settingsManager.getCurrency(currencyId);
        Account account = accountManager.getAccount(principal.getName());
        assertCanWithdraw(currency, account, Currency.Type.CRYPTO); // Check prerequisites
        VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currency);
        Address.Transaction transaction = daemonManager.withdrawFunds(virtualWallet, address, amount);
        log.info(String.format("Withdraw success: %s %s => %s", amount, currency.getCode(), address));
        return transaction;
    }
}
