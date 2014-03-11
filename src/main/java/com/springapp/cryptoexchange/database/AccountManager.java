package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.database.model.log.LoginHistory;

import java.math.BigDecimal;
import java.util.List;

public interface AccountManager {
    public VirtualWallet getVirtualWallet(Account account, Currency currency) throws Exception;
    public BigDecimal getVirtualWalletBalance(VirtualWallet wallet) throws Exception;
    public Account addAccount(Account account) throws Exception;
    public Account getAccount(String login);
    public void setAccountEnabled(long id, boolean enabled);
    public void logEntry(Account account, String ip, String userAgentString);
    public List<LoginHistory> getLastEntriesByAccount(Account account, int maxDaysAgo, int max);
    public List<LoginHistory> getLastEntriesByIp(String ip, int maxDaysAgo, int max);
    public List<Order> getAccountOrders(Account account, int max);
    public List<Order> getAccountOrdersByPair(TradingPair tradingPair, Account account, int max);
}
