package com.springapp.cryptoexchange.utils;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Service
public class CacheCleaner { // Костыль
    @Caching(evict = {
            @CacheEvict(value = "getAccountBalances", key = "#firstOrder.account.login"),
            @CacheEvict(value = "getAccountBalances", key = "#secondOrder.account.login"),
            @CacheEvict(value = "getAccountOrdersByPair", key = "#firstOrder.account.login + #firstOrder.tradingPair.id"),
            @CacheEvict(value = "getAccountOrders", key = "#firstOrder.account.login"),
            @CacheEvict(value = "getAccountOrdersByPair", key = "#secondOrder.account.login + #secondOrder.tradingPair.id"),
            @CacheEvict(value = "getAccountOrders", key = "#secondOrder.account.login")
    })
    public void orderExecutionEvict(Order firstOrder, Order secondOrder) {
        // nothing
    }

    @Caching(evict = {
            @CacheEvict(value = "getMarketPrices", key = "#tradingPair.id")
    })
    public void marketPricesEvict(TradingPair tradingPair) {
        // nothing
    }

    @Caching(evict = {
            @CacheEvict(value = "getAccountBalances", key = "#account.login")
    })
    public void balancesEvict(Account account) {
        // nothing
    }
}
