package com.springapp.cryptoexchange.webapi.admin;

import com.springapp.cryptoexchange.database.DaemonManager;
import com.springapp.cryptoexchange.database.NewsManager;
import com.springapp.cryptoexchange.database.SettingsManager;
import com.springapp.cryptoexchange.database.model.*;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    DaemonManager daemonManager;

    @Autowired
    NewsManager newsManager;

    @Transactional
    @ResponseBody
    @RequestMapping(value = "/fee", method = RequestMethod.GET)
    @SuppressWarnings("unchecked")
    public List<FreeBalance> getFreeBalance() {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(FreeBalance.class)
                .add(Restrictions.gt("amount", BigDecimal.ZERO))
                .list();
    }

    @Transactional
    @ResponseBody
    @RequestMapping(value = "/fee/{currencyId}", method = RequestMethod.GET)
    @SuppressWarnings("unchecked")
    public List<FreeBalance> getFreeBalance(@PathVariable long currencyId) {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        return session.createCriteria(FreeBalance.class)
                .add(Restrictions.eq("currency", currency))
                .add(Restrictions.gt("amount", BigDecimal.ZERO))
                .list();
    }

    @Transactional
    @RequestMapping(value = "/modify_currency/{currencyId}", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @Caching(evict = {
            @CacheEvict(value = "getCurrencies", allEntries = true),
            @CacheEvict(value = "getCurrencyInfo", key = "#currencyId"),
            @CacheEvict(value = "getTradingPairs", allEntries = true),
            @CacheEvict(value = "getMarketPrices", allEntries = true)
    })
    public Currency modifyCurrency(@PathVariable long currencyId, @RequestParam(required = false) boolean enabled, @RequestParam String currencyCode, @RequestParam String currencyName, @RequestParam Currency.CurrencyType currencyType, @RequestParam BigDecimal withdrawFee, @RequestParam BigDecimal minimalWithdrawAmount) {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        currency.setEnabled(enabled);
        currency.setCurrencyCode(currencyCode);
        currency.setCurrencyName(currencyName);
        currency.setCurrencyType(currencyType);
        currency.setWithdrawFee(withdrawFee);
        currency.setMinimalWithdrawAmount(minimalWithdrawAmount);
        session.update(currency);
        log.info("Currency modified: " + currency);
        return currency;
    }

    @Transactional
    @RequestMapping(value = "/add_currency", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @Caching(evict = {
            @CacheEvict(value = "getCurrencies", allEntries = true)
    })
    public Currency addCurrency(@RequestParam String currencyCode, @RequestParam String currencyName, @RequestParam Currency.CurrencyType currencyType, @RequestParam BigDecimal withdrawFee, @RequestParam BigDecimal minimalWithdrawAmount) {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = new Currency(currencyCode, currencyName, currencyType);
        currency.setWithdrawFee(withdrawFee);
        currency.setMinimalWithdrawAmount(minimalWithdrawAmount);
        if(currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
            currency.setEnabled(false); // Daemon not configured
        }
        session.save(currency);
        log.info("New currency added: " + currency);
        return currency;
    }

    @Transactional
    @RequestMapping(value = "/modify_trading_pair/{tradingPairId}", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @Caching(evict = {
            @CacheEvict(value = "getTradingPairs", allEntries = true),
            @CacheEvict(value = "getMarketPrices", key = "#tradingPairId")
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

    @Transactional
    @RequestMapping(value = "/add_trading_pair", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @Caching(evict = {
            @CacheEvict(value = "getTradingPairs", allEntries = true)
    })
    public TradingPair addTradingPair(@RequestParam long firstCurrencyId, @RequestParam long secondCurrencyId) {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = settingsManager.getCurrency(firstCurrencyId), currency1 = settingsManager.getCurrency(secondCurrencyId);
        Assert.isTrue(currency != null && currency1 != null, "Invalid currency ID");
        TradingPair tradingPair = new TradingPair(currency, currency1);
        session.save(tradingPair);
        log.info("New trading pair added: " + tradingPair);
        return tradingPair;
    }

    @Transactional
    @RequestMapping(value = "/set_daemon_settings/{currencyId}", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    @Caching(evict = {
            @CacheEvict(value = "getCryptoBalance", allEntries = true)
    })
    public boolean setDaemonSettings(@PathVariable long currencyId, @RequestParam String daemonHost, @RequestParam Integer daemonPort, @RequestParam String daemonLogin, @RequestParam String daemonPassword) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        Assert.isTrue(currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO), "Invalid currency type");
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
        log.info("Daemon settings changed for currency: " + currency);
        return true;
    }

    @Transactional
    @RequestMapping(value = "/commit_news", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    public News commitNews(@RequestParam long id, @RequestParam String title, @RequestParam String text) {
        News news = new News(title, text);
        news.setId(id);
        newsManager.addOrModifyNews(news);
        return news;
    }

    @Transactional
    @RequestMapping(value = "/remove_news", method = RequestMethod.POST, headers = "X-Ajax-Call=true")
    @ResponseBody
    public boolean removeNews(@RequestParam long id) {
        newsManager.removeNews(id);
        return true;
    }
}
