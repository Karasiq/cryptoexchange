package com.springapp.cryptoexchange;

import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import com.bitcoin.daemon.TestingWallet;
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
import org.springframework.web.context.WebApplicationContext;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;

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
        // this.mockMvc = webAppContextSetup(this.wac).build();
        settingsManager.setTestingMode(true);
        settingsManager.init();
        daemonManager.init();
        marketManager.init();
    }

    /*@Test
    public void simple() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("hello"));
    } */

    //@Test
    public void jsonRpc() throws Exception {
        // Existing:
        JsonRPC rpc = new JsonRPC("localhost", 8779, "user", "password");
        AbstractWallet account = new CryptoCoinWallet.Account("PZcojt26ozH2nh5u7zqG1DfuzG6FUuvbZ3");

        account.loadTransactions(rpc, 1000);
        System.out.println(account.summaryConfirmedBalance());

        // New:
        AbstractWallet account1 = CryptoCoinWallet.generateAccount(rpc, "test");
        account1.loadAddresses(rpc);

        System.out.println(account.sendToAddress(rpc, account1.generateNewAddress(rpc).getAddress(), new BigDecimal(3.0)));
        System.out.println(account.summaryConfirmedBalance());
    }

    @Test
    @Transactional
    public void accountTest() throws Exception {
        JsonRPC rpc = new JsonRPC("localhost", 8779, "user", "password");
        AbstractWallet walletAccount = new TestingWallet("PZcojt26ozH2nh5u7zqG1DfuzG6FUuvbZ3");

        List<Currency> currencyList = settingsManager.getCurrencyList();
        Account account = accountManager.getAccount("username");
        if(account == null) {
            account = accountManager.addAccount(new Account("username", "password"));
        }

        String address = accountManager.createWalletAddress(accountManager.getVirtualWallet(account, currencyList.get(0)), walletAccount, rpc);
        System.out.println(address);
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

        firstWallet.setVirtualBalance(new BigDecimal(10));
        secondWallet.setVirtualBalance(new BigDecimal(10));

        Order buyOrder = marketManager.executeOrder(new Order(Order.Type.BUY, new BigDecimal(1), new BigDecimal(5), tradingPair, firstWallet, secondWallet, account));

        Order sellOrder = marketManager.executeOrder(new Order(Order.Type.SELL, new BigDecimal(2), new BigDecimal(3), tradingPair, secondWallet, firstWallet, account));
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
