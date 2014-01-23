package com.springapp.cryptoexchange;

import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import com.springapp.cryptoexchange.database.AccountManager;
import com.springapp.cryptoexchange.database.SettingsManager;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration("file:src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml")
public class AppTests {
    @Autowired
    SettingsManager settingsManager;

    @Autowired
    AccountManager accountManager;

    private MockMvc mockMvc;

    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Autowired
    protected WebApplicationContext wac;

    /* @Before
    public void setup() {
        this.mockMvc = webAppContextSetup(this.wac).build();
    }

    @Test
    public void simple() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("hello"));
    } */

    //@Test
    public void jsonRpc() throws Exception {
        // Existing:
        JsonRPC rpc = new JsonRPC("localhost", 8779, "user", "password");
        CryptoCoinWallet.Account account = new CryptoCoinWallet.Account("PZcojt26ozH2nh5u7zqG1DfuzG6FUuvbZ3");

        account.loadTransactions(rpc, 1000);
        System.out.println(account.summaryConfirmedBalance());

        // New:
        CryptoCoinWallet.Account account1 = CryptoCoinWallet.generateAccount(rpc, "test");
        account1.loadAddresses(rpc);

        System.out.println(account.sendToAddress(rpc, account1.generateNewAddress(rpc).getAddress(), new BigDecimal(3.0)));
        System.out.println(account.summaryConfirmedBalance());
    }

    @Test
    public void accountTest() throws Exception {
        JsonRPC rpc = new JsonRPC("localhost", 8779, "user", "password");
        CryptoCoinWallet.Account walletAccount = new CryptoCoinWallet.Account("PZcojt26ozH2nh5u7zqG1DfuzG6FUuvbZ3");

        List<Currency> currencyList = settingsManager.getCurrencyList();
        Account account = accountManager.getAccount("username");
        String address = accountManager.createWalletAddress(accountManager.getVirtualWallet(account, currencyList.get(0)), walletAccount, rpc);
        System.out.println(address);
    }
}
