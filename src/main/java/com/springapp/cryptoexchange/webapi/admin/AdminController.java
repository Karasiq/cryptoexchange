package com.springapp.cryptoexchange.webapi.admin;

import com.springapp.cryptoexchange.database.AbstractDaemonManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.Daemon;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Secured("ROLE_ADMIN")
@RequestMapping(value = "/rest/admin.json", headers = "X-Ajax-Call=true")
public class AdminController {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractDaemonManager daemonManager;

    @Transactional
    @RequestMapping(value = "/add_currency", method = RequestMethod.POST)
    @ResponseBody
    public Currency addCurrency(@RequestParam String currencyCode, @RequestParam String currencyName, @RequestParam Currency.CurrencyType currencyType) {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = new Currency(currencyCode, currencyName, currencyType);
        session.save(currency);
        return currency;
    }

    @Transactional
    @RequestMapping(value = "/add_trading_pair", method = RequestMethod.POST)
    @ResponseBody
    public TradingPair addTradingPair(@RequestParam long firstCurrencyId, @RequestParam long secondCurrencyId) {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = settingsManager.getCurrency(firstCurrencyId), currency1 = settingsManager.getCurrency(secondCurrencyId);
        Assert.isTrue(currency != null && currency1 != null, "Invalid currency ID");
        TradingPair tradingPair = new TradingPair(currency, currency1);
        session.save(tradingPair);
        return tradingPair;
    }

    @Transactional
    @RequestMapping(value = "/set_daemon_settings", method = RequestMethod.POST)
    @ResponseBody
    public boolean setDaemonSettings(@RequestParam long currencyId, @RequestParam String daemonHost, @RequestParam Integer daemonPort, @RequestParam String daemonLogin, @RequestParam String daemonPassword) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = settingsManager.getCurrency(currencyId);
        Assert.notNull(currency, "Currency not found");
        Assert.isTrue(currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO), "Invalid currency type");
        Daemon daemon = (Daemon) session.createCriteria(Daemon.class)
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
        if(daemon != null) {
            session.delete(daemon);
        }
        daemon = new Daemon(currency, daemonHost, daemonPort, daemonLogin, daemonPassword);
        session.save(daemon);
        daemonManager.loadDaemons();
        return true;
    }
}
