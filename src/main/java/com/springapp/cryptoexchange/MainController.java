package com.springapp.cryptoexchange;

import com.springapp.cryptoexchange.database.AbstractAccountManager;
import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractMarketManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.webapi.AbstractConvertService;
import lombok.Cleanup;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/")
public class MainController {
    @Autowired
    SessionFactory sessionFactory;

    public Account testAccount() throws Exception {
        settingsManager.setTestingMode(true);
        Account account = accountManager.getAccount("test");
        if(account == null) {
            List<Currency> currencyList = settingsManager.getCurrencyList();
            account = accountManager.addAccount(new Account("test", "test"));

            VirtualWallet virtualWallet = accountManager.getVirtualWallet(account, currencyList.get(1));
            virtualWallet.addBalance(BigDecimal.TEN);

            virtualWallet = accountManager.getVirtualWallet(account, currencyList.get(1));
            virtualWallet.addBalance(BigDecimal.TEN);
        }
        return account;
    }

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractMarketManager marketManager;

    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractAccountManager accountManager;

    @Autowired
    AbstractConvertService convertService;

    @RequestMapping(value = "/market/{marketId}", method = RequestMethod.GET)
    public String marketPage(ModelMap model, @PathVariable long marketId) throws Exception {
        TradingPair tradingPair = settingsManager.getTradingPair(marketId);
        model.addAttribute("tradingPair", tradingPair);
        model.addAttribute("history", convertService.getMarketHistory(tradingPair));
        model.addAttribute("depth", convertService.getMarketDepth(tradingPair));
        model.addAttribute("account", testAccount());
        return "market";
    }

	@RequestMapping(method = RequestMethod.GET)
	public String indexPage(ModelMap model) {
        model.addAttribute("tradingPairs", settingsManager.getTradingPairs());
		return "index";
	}

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String loginPage(ModelMap model) {
        return "login";
    }
}