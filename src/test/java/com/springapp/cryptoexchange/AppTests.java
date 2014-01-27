package com.springapp.cryptoexchange;

import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import com.springapp.cryptoexchange.database.*;
import com.springapp.cryptoexchange.database.model.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration("file:src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml")
public class AppTests {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AbstractDaemonManager daemonManager;

    @Autowired
    AbstractAccountManager accountManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractMarketManager marketManager;

    private MockMvc mockMvc;

    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Autowired
    protected WebApplicationContext wac;

    @Before
    public void setup() throws Exception {
        this.mockMvc = webAppContextSetup(this.wac).build();
        settingsManager.setTestingMode(true);
    }

    @Test
    public void mainPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("hello"));
    }

    @Test
    public void jsonApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/rest-api/info/1"))
                .andExpect(status().isOk())
                .andReturn();
        System.out.println(result.getResponse().getContentAsString());

        result = mockMvc.perform(get("/rest-api/info"))
                .andExpect(status().isOk())
                .andReturn();
        System.out.println(result.getResponse().getContentAsString());
    }

    //@Test
    public void jsonRpc() throws Exception {
        // Existing:
        JsonRPC rpc = new JsonRPC("localhost", 8779, "user", "password");
        CryptoCoinWallet.Account account = new CryptoCoinWallet.Account(rpc, "PZcojt26ozH2nh5u7zqG1DfuzG6FUuvbZ3");

        account.loadTransactions(1000);
        System.out.println(account.summaryConfirmedBalance());

        // New:
        CryptoCoinWallet.Account account1 = CryptoCoinWallet.generateAccount(rpc, "test");
        System.out.println(account.sendToAddress(account1.generateNewAddress().getAddress(), BigDecimal.valueOf(3.0)));
        System.out.println(account.summaryConfirmedBalance());
    }

    @Test
    @Transactional
    public void accountTest() throws Exception {
        List<Currency> currencyList = settingsManager.getCurrencyList();
        Account account = accountManager.getAccount("username");
        if(account == null) {
            account = accountManager.addAccount(new Account("username", "password"));
        }
        System.out.println(currencyList);
        System.out.println(account);
    }

    @Test
    @Transactional
    public void marketTest() throws Exception {
        Session session = sessionFactory.getCurrentSession();
        Account account = accountManager.getAccount("username");
        if(account == null) {
            account = accountManager.addAccount(new Account("username", "password"));
        }
        sessionFactory.getCurrentSession().update(account);
        TradingPair tradingPair = settingsManager.getTradingPairs().get(0);
        VirtualWallet firstWallet = account.createVirtualWallet(tradingPair.getFirstCurrency()), secondWallet = account.createVirtualWallet(tradingPair.getSecondCurrency());

        firstWallet.setVirtualBalance(BigDecimal.valueOf(10));
        secondWallet.setVirtualBalance(BigDecimal.valueOf(10));

        Order buyOrder = marketManager.executeOrder(new Order(Order.Type.BUY, BigDecimal.valueOf(1), BigDecimal.valueOf(5), tradingPair, firstWallet, secondWallet, account));

        Order sellOrder = marketManager.executeOrder(new Order(Order.Type.SELL, BigDecimal.valueOf(2), BigDecimal.valueOf(3), tradingPair, secondWallet, firstWallet, account));
        System.out.println(sellOrder);
        marketManager.cancelOrder(sellOrder);

        session.update(buyOrder);
        System.out.println(buyOrder);
        System.out.println(sellOrder);

        System.out.println(firstWallet.getVirtualBalance());
        System.out.println(secondWallet.getVirtualBalance());

        List<Candle> history = historyManager.getMarketChartData(tradingPair, 24);
        System.out.println(history);
    }
}
