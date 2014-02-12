package com.springapp.cryptoexchange.webapi.slave;

import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.webapi.ApiDefs;
import com.springapp.cryptoexchange.webapi.master.TradingController;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;

@Controller
@CommonsLog
@Secured("ROLE_USER")
@RequestMapping("/rest/trade.json")
@Profile("slave")
public class TradingControllerProxy implements TradingController {
    @Autowired
    TradingController remoteTradingController;

    @Override
    @RequestMapping(value = "/order/create/{tradingPairId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("all")
    public ApiDefs.ApiStatus<Long> createOrder(@PathVariable long tradingPairId, @RequestParam Order.Type type, @RequestParam BigDecimal price, @RequestParam BigDecimal amount, Principal principal) {
        return remoteTradingController.createOrder(tradingPairId, type, price, amount, principal);
    }

    @Override
    @RequestMapping(value = "/order/{orderId}/cancel", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ApiDefs.ApiStatus cancelOrder(@PathVariable long orderId, Principal principal) {
        return remoteTradingController.cancelOrder(orderId, principal);
    }
}
