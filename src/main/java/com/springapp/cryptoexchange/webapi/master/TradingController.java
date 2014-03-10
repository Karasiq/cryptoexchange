package com.springapp.cryptoexchange.webapi.master;

import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.webapi.ApiDefs;

import java.math.BigDecimal;
import java.security.Principal;

public interface TradingController {
    long createOrder(long tradingPairId, Order.Type type, BigDecimal price, BigDecimal amount, Principal principal) throws Exception;
    void cancelOrder(long orderId, Principal principal) throws Exception;
}
