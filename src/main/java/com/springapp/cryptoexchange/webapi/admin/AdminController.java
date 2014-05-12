package com.springapp.cryptoexchange.webapi.admin;

import com.bitcoin.daemon.Address;
import com.springapp.cryptoexchange.database.*;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.utils.CacheCleaner;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
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
@Secured("ROLE_ADMIN")
@RequestMapping(value = "/rest/admin.json")
@CommonsLog
public class AdminController {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    @Lazy
    DaemonManager daemonManager;

    @Autowired
    NewsManager newsManager;

    @Autowired
    FeeManager feeManager;

    @Autowired
    AccountManager accountManager;

    @Autowired
    CacheCleaner cacheCleaner;

    @Transactional(readOnly = true)
    @ResponseBody
    @RequestMapping(value = "/fee", method = RequestMethod.GET)
    @SuppressWarnings("unchecked")
    public List<FreeBalance> getFreeBalance() {
        return feeManager.getFreeBalances();
    }

    @Transactional(readOnly = true)
    @ResponseBody
    @RequestMapping(value = "/fee/{currencyId}", method = RequestMethod.GET)
    public FreeBalance getFreeBalance(@PathVariable long currencyId) {
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        return feeManager.getFreeBalance(currency);
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @RequestMapping(value = "/currency/{currencyId}/modify", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @Caching(evict = {
            @CacheEvict(value = "getCurrencies", allEntries = true),
            @CacheEvict(value = "getCurrencyInfo", key = "#currencyId"),
            @CacheEvict(value = "getTradingPairs", allEntries = true),
            @CacheEvict(value = "getTradingPairInfo", allEntries = true),
            @CacheEvict(value = "getAccountBalances", allEntries = true)
    })
    public Currency modifyCurrency(@PathVariable long currencyId, @RequestParam(required = false) boolean enabled, @RequestParam String code, @RequestParam String name, @RequestParam Currency.Type type, @RequestParam BigDecimal withdrawFee, @RequestParam BigDecimal minimalWithdrawAmount) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        currency.setEnabled(enabled);
        currency.setCode(code);
        currency.setName(name);
        currency.setType(type);
        currency.setWithdrawFee(withdrawFee);
        currency.setMinimalWithdrawAmount(minimalWithdrawAmount);
        session.update(currency);
        log.info("Currency modified: " + currency);
        if (currency.getType().equals(Currency.Type.CRYPTO)) {
            daemonManager.loadDaemons();
        }
        return currency;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    @RequestMapping(value = "/currency/add", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @Caching(evict = {
            @CacheEvict(value = "getCurrencies", allEntries = true),
            @CacheEvict(value = "getAccountBalances", allEntries = true)
    })
    public Currency addCurrency(@RequestParam String code, @RequestParam String name, @RequestParam Currency.Type type, @RequestParam BigDecimal withdrawFee, @RequestParam BigDecimal minimalWithdrawAmount) throws Exception {
        Currency currency = new Currency(code, name, type);
        currency.setWithdrawFee(withdrawFee);
        currency.setMinimalWithdrawAmount(minimalWithdrawAmount);
        if(currency.getType().equals(Currency.Type.CRYPTO)) {
            currency.setEnabled(false); // Daemon not configured
        }
        settingsManager.addCurrency(currency);
        return currency;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @RequestMapping(value = "/trading_pair/{tradingPairId}/modify", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @Caching(evict = {
            @CacheEvict(value = "getTradingPairs", allEntries = true),
            @CacheEvict(value = "getTradingPairInfo", key = "#tradingPairId")
    })
    public TradingPair modifyTradingPair(@PathVariable long tradingPairId, @RequestParam String name, @RequestParam String description, @RequestParam(required = false) boolean enabled, @RequestParam BigDecimal minimalTradeAmount, @RequestParam BigDecimal tradingFee) {
        Session session = sessionFactory.getCurrentSession();
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Assert.notNull(tradingPair, "Trading pair not found");
        tradingPair.setName(name);
        tradingPair.setEnabled(enabled);
        tradingPair.setDescription(description);
        tradingPair.setMinimalTradeAmount(minimalTradeAmount);
        tradingPair.setTradingFee(tradingFee);
        session.update(tradingPair);
        log.info("Trading pair modified: " + tradingPair);
        return tradingPair;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    @RequestMapping(value = "/trading_pair/add", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @Caching(evict = {
            @CacheEvict(value = "getTradingPairs", allEntries = true)
    })
    public TradingPair addTradingPair(@RequestParam long firstCurrencyId, @RequestParam long secondCurrencyId) throws Exception {
        Assert.isTrue(firstCurrencyId != secondCurrencyId, "Currencies must not be the same");
        Currency currency = settingsManager.getCurrency(firstCurrencyId), currency1 = settingsManager.getCurrency(secondCurrencyId);
        Assert.isTrue(currency != null && currency1 != null, "Invalid currency ID");
        TradingPair tradingPair = new TradingPair(currency, currency1);
        settingsManager.addTradingPair(tradingPair);
        return tradingPair;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    @RequestMapping(value = "/trading_pair/{tradingPairId}/delete", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    public boolean removeTradingPair(@PathVariable long tradingPairId) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(tradingPairId);
        Assert.notNull(tradingPair, "Trading pair not found");
        settingsManager.removeTradingPair(tradingPair);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @RequestMapping(value = "/daemon/{currencyId}/set", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    public boolean setDaemonSettings(@PathVariable long currencyId, @RequestParam String daemonHost, @RequestParam Integer daemonPort, @RequestParam String daemonLogin, @RequestParam String daemonPassword) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        Assert.isTrue(currency.getType().equals(Currency.Type.CRYPTO), "Invalid currency type");
        Daemon daemon = daemonManager.getDaemonSettings(currency);
        if(daemon == null) {
            daemon = new Daemon();
            daemon.setCurrency(currency);
        }
        daemon.setDaemonHost(daemonHost);
        daemon.setDaemonPort(daemonPort);
        daemon.setDaemonLogin(daemonLogin);
        daemon.setDaemonPassword(daemonPassword);

        session.saveOrUpdate(daemon);
        daemonManager.setDaemonSettings(daemon);
        cacheCleaner.cryptoBalanceEvict();
        return true;
    }

    @Transactional
    @RequestMapping(value = "/news/commit", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    public News commitNews(@RequestParam long id, @RequestParam String title, @RequestParam String text) {
        News news = new News(title, text);
        news.setId(id);
        newsManager.addOrModifyNews(news);
        return news;
    }

    @Transactional
    @RequestMapping(value = "/news/{newsId}/remove", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    public boolean removeNews(@PathVariable long newsId) {
        newsManager.removeNews(newsId);
        return true;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @RequestMapping(value = "/fee/{currencyId}/withdraw/crypto", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    public Address.Transaction withdrawCryptoFee(@PathVariable long currencyId, @RequestParam String address, @RequestParam BigDecimal amount) throws Exception {
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.isTrue(currency != null && currency.isEnabled() && currency.getType().equals(Currency.Type.CRYPTO), "Invalid parameters");
        return (Address.Transaction) feeManager.withdrawFee(currency, amount, address);
    }

    @Caching(evict = {
            @CacheEvict(value = "getAccountBalances", key = "#username"),
            @CacheEvict(value = "getAccountBalances", key = "#principal.name")
    })
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @RequestMapping(value = "/fee/{currencyId}/withdraw/internal", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @SuppressWarnings("all")
    public boolean withdrawInternalFee(@PathVariable long currencyId, @RequestParam(required = false, defaultValue = "") String username, @RequestParam BigDecimal amount, Principal principal) throws Exception {
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        if(username.length() < 1) username = principal.getName();
        Account account = accountManager.getAccount(username);
        Assert.isTrue(account != null && account.isEnabled(), "Account not found or disabled");
        log.info(String.format("Internal fee withdrawal requested: %s => %s", amount, account));
        feeManager.withdrawFee(currency, amount, accountManager.getVirtualWallet(account, currency));
        return true;
    }

    @ResponseBody
    @RequestMapping(value = "/fee/reset", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    public boolean recalculateFreeBalance() throws Exception {
        ((FeeManagerImpl) feeManager).calculateDivergence();
        return true;
    }
}
