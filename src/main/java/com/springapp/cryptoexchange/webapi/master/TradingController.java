package com.springapp.cryptoexchange.webapi.master;

import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.webapi.ApiDefs;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.security.Principal;

public interface TradingController {
    ApiDefs.ApiStatus<Long> createOrder(long tradingPairId, Order.Type type, BigDecimal price, BigDecimal amount, Principal principal);
    ApiDefs.ApiStatus cancelOrder(long orderId, Principal principal);
}
