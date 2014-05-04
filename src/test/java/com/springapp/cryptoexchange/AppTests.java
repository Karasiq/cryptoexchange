package com.springapp.cryptoexchange;

import com.bitcoin.daemon.*;
import com.bitcoin.daemon.Address;
import com.springapp.cryptoexchange.database.*;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.utils.Calculator;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import lombok.val;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration("file:src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml")
@CommonsLog
@ActiveProfiles({"master", "data"})
@FieldDefaults(level = AccessLevel.PROTECTED)
public class AppTests {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    DaemonManager daemonManager;

    @Autowired
    AccountManager accountManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    HistoryManager historyManager;

    @Autowired
    MarketManager marketManager;

    @Autowired
    FeeManager feeManager;

    MockMvc mockMvc;

    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Autowired
    WebApplicationContext wac;

    @Before
    public void setup() throws Exception {
        this.mockMvc = webAppContextSetup(this.wac).build();
        // daemonManager.loadTransactions();
    }

    @Test
    public void mainPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    public void cleanTest() throws Exception {
        ((AccountManagerImpl)accountManager).entryLogAutoClean();
        ((MarketManagerImpl)marketManager).cleanOrders();
        ((FeeManagerImpl)feeManager).calculateDivergence();
    }

    @Test
    public void jsonApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/rest/api.json/trading_pairs"))
                .andExpect(status().isOk())
                .andReturn();
        log.debug(result.getResponse().getContentAsString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void jsonRpc() throws Exception {
        Currency currency = settingsManager.getCurrencyList().get(0);
        AbstractWallet<String, Address.Transaction> wallet = daemonManager.getAccount(currency);

        Map<String, ?> info = ((CryptoCoinWallet) wallet).getInfo();
        Assert.isTrue((Integer) info.get("blocks") > 0);
        log.info("getinfo: " + info);

        final Set<String> addressSet = wallet.getAddressSet();
        BigDecimal balance = wallet.summaryConfirmedBalance(addressSet);

        int iterations = 1000;
        long startTime = System.nanoTime();
        for(int i = 0; i < iterations; i++) {
            Assert.isTrue(balance.equals(wallet.summaryConfirmedBalance(addressSet)));
        }
        log.info(String.format("Balance retrieved %d times in %d ms (%d ms per one)", iterations,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime),
                TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - startTime) / iterations)));

        log.info(String.format("%s wallet balance = %s %s", currency.getCurrencyName(),
                balance, currency.getCurrencyCode()));
    }

    @Test
    @Transactional
    public void account() throws Exception {
        log.debug(accountManager.addAccount(new Account("username", "username@mail.com", "password")));
        log.debug(accountManager.getAccount("username"));
    }

    @Test
    @Transactional
    public void market() throws Exception {
        Session session = sessionFactory.getCurrentSession();
        Account firstAccount = accountManager.getAccount("buyer"), secondAccount = accountManager.getAccount("seller");
        if(firstAccount == null) {
            firstAccount = accountManager.addAccount(new Account("buyer", "buyer@email.com", "password"));
        }
        if(secondAccount == null) {
            secondAccount = accountManager.addAccount(new Account("seller", "seller@email.com", "password"));
        }
        TradingPair tradingPair = settingsManager.getTradingPairs().get(0);
        tradingPair.setMinimalTradeAmount(BigDecimal.ZERO);

        VirtualWallet firstBuyWallet = accountManager.getVirtualWallet(firstAccount, tradingPair.getFirstCurrency()),
                secondBuyWallet = accountManager.getVirtualWallet(firstAccount, tradingPair.getSecondCurrency()),
                firstSellWallet = accountManager.getVirtualWallet(secondAccount, tradingPair.getFirstCurrency()),
                secondSellWallet = accountManager.getVirtualWallet(secondAccount, tradingPair.getSecondCurrency());

        final BigDecimal walletBalanceAmount = BigDecimal.valueOf(10), // Initial balance
                sellAmount = BigDecimal.valueOf(2), sellPrice = BigDecimal.valueOf(3), // Sell order
                buyAmount = BigDecimal.valueOf(1), buyPrice = BigDecimal.valueOf(5); // Buy order

        secondBuyWallet.setVirtualBalance(walletBalanceAmount); // Buyer source
        firstSellWallet.setVirtualBalance(walletBalanceAmount); // Seller source
        session.saveOrUpdate(secondBuyWallet);
        session.saveOrUpdate(firstSellWallet);

        Order sellOrder = marketManager.executeOrder(new Order(Order.Type.SELL, sellAmount, sellPrice, tradingPair, firstSellWallet, secondSellWallet, firstAccount));

        Order buyOrder = marketManager.executeOrder(new Order(Order.Type.BUY, buyAmount, buyPrice, tradingPair, secondBuyWallet, firstBuyWallet, firstAccount));

        Assert.isTrue(buyOrder.getStatus().equals(Order.Status.COMPLETED) && sellOrder.getStatus().equals(Order.Status.PARTIALLY_COMPLETED));

        marketManager.cancelOrder(sellOrder);
        Assert.isTrue(sellOrder.getStatus().equals(Order.Status.PARTIALLY_CANCELLED));

        // Check balance validity
        Assert.isTrue(firstBuyWallet.getVirtualBalance()
                .compareTo(Calculator.withoutFee(buyAmount, tradingPair.getTradingFee())) == 0, "Buyer dest balance is invalid");
        Assert.isTrue(secondSellWallet.getVirtualBalance()
                .compareTo(Calculator.withoutFee(buyAmount.multiply(sellPrice), tradingPair.getTradingFee())) == 0, "Seller dest balance is invalid");

        Assert.isTrue(secondBuyWallet.getVirtualBalance()
                .compareTo(walletBalanceAmount.subtract(sellPrice.multiply(buyAmount))) == 0, "Buyer source balance is invalid");
        Assert.isTrue(firstSellWallet.getVirtualBalance()
                .compareTo(walletBalanceAmount.subtract(buyAmount)) == 0, "Seller source balance is invalid");

        log.info(buyOrder);
        log.info(sellOrder);

        log.debug("Source: " + secondBuyWallet.getVirtualBalance() + " " + firstSellWallet.getVirtualBalance() +
                "\nDest: " + firstBuyWallet.getVirtualBalance() + " " + secondSellWallet.getVirtualBalance());

        List<Candle> history = tradingPair.getHistory();
        log.info(history.get(0));
        Assert.isTrue(history.get(0).getClose().compareTo(sellPrice) == 0, "Invalid chart");
    }

    @Test
    @Transactional
    public void deleteTradingPair() throws Exception {
        TradingPair tradingPair = new TradingPair(new Currency("TEST1", "TestCurrency1", Currency.CurrencyType.PURE_VIRTUAL),
                new Currency("TEST2", "TestCurrency2", Currency.CurrencyType.PURE_VIRTUAL));

        settingsManager.addCurrency(tradingPair.getFirstCurrency());
        settingsManager.addCurrency(tradingPair.getSecondCurrency());
        settingsManager.addTradingPair(tradingPair);
        log.debug(tradingPair);

        Account account = new Account("testaccount", "testaccount@mail.com", "testaccount");
        accountManager.addAccount(account);
        log.debug(account);

        VirtualWallet virtualWalletSource = accountManager.getVirtualWallet(account, tradingPair.getSecondCurrency()),
        virtualWalletDest = accountManager.getVirtualWallet(account, tradingPair.getFirstCurrency());

        int initialBalance = 100;

        virtualWalletSource.addBalance(BigDecimal.valueOf(initialBalance));
        log.debug(virtualWalletSource);
        log.debug(virtualWalletDest);

        // One cancelled:
        marketManager.cancelOrder(marketManager.executeOrder(new Order(Order.Type.BUY, BigDecimal.ONE, BigDecimal.ONE, tradingPair, virtualWalletSource, virtualWalletDest, account)));

        long startTime = System.nanoTime();
        for(int i = 0; i < initialBalance; i++) marketManager.executeOrder(new Order(Order.Type.BUY, BigDecimal.ONE, BigDecimal.ONE, tradingPair, virtualWalletSource, virtualWalletDest, account));
        log.info(String.format("%d orders executed in %d ms", initialBalance, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)));

        startTime = System.nanoTime();
        settingsManager.removeTradingPair(tradingPair);
        Assert.isTrue(accountManager.getVirtualWalletBalance(virtualWalletSource)
                .compareTo(BigDecimal.valueOf(initialBalance)) == 0, "Invalid resulting balances");
        log.info(String.format("%d orders cancelled and deleted in %d ms", initialBalance, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)));
    }
}
