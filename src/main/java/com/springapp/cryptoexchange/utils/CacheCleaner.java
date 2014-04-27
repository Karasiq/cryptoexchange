package com.springapp.cryptoexchange.utils;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Service
public class CacheCleaner {
    @Autowired
    CacheManager cacheManager;

    public void cryptoBalanceEvict() {
        cacheManager.getCache("getCryptoBalance").clear();
        cacheManager.getCache("getTransactions").clear();
        cacheManager.getCache("getAccountBalances").clear();
    }

    private void clearOrderCache(Order order) {
        String login = order.getAccount().getLogin();
        Cache balance = cacheManager.getCache("getAccountBalances"),
                orders = cacheManager.getCache("getAccountOrders"),
                ordersByPair = cacheManager.getCache("getAccountOrdersByPair");
        balance.evict(login);
        ordersByPair.evict(login + "/" + order.getTradingPair().getId());
        orders.evict(login);
    }

    public void orderExecutionEvict(Order firstOrder, Order secondOrder) {
        clearOrderCache(firstOrder);
        clearOrderCache(secondOrder);
    }

    public void marketPricesEvict(TradingPair tradingPair) {
        cacheManager.getCache("getTradingPairInfo").evict(tradingPair.getId());
    }

    public void balancesEvict(Account account) {
        cacheManager.getCache("getAccountBalances").evict(account.getLogin());
    }
}
