package com.springapp.cryptoexchange;

import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration("file:src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml")
public class AppTests {
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

    @Test
    public void jsonRpc() throws Exception {
        // Existing:
        JsonRPC rpc = new JsonRPC("localhost", 8779, "user", "password");
        CryptoCoinWallet.Account account = new CryptoCoinWallet.Account("PZcojt26ozH2nh5u7zqG1DfuzG6FUuvbZ3");

        account.loadTransactions(rpc, 1000);
        System.out.println(account.summaryConfirmedBalance());

        // New:
        // CryptoCoinWallet.Account account1 = new CryptoCoinWallet.Account("e36783ef-2c28-4ec7-86e1-682697b93b4a");//CryptoCoinWallet.generateAccount(rpc);
        // account1.loadAddresses(rpc);

        // System.out.println(account.sendToAddress(rpc, account.generateNewAddress(rpc), new BigDecimal(3.0)));
       // System.out.println(account1);

    }
}
