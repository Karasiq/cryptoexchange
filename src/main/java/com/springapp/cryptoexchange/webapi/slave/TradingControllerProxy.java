package com.springapp.cryptoexchange.webapi.slave;

import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.webapi.ApiDefs;
import com.springapp.cryptoexchange.webapi.master.TradingController;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
    @Override
    @RequestMapping(value = "/order/create/{tradingPairId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("all")
    public long createOrder(@PathVariable long tradingPairId, @RequestParam Order.Type type, @RequestParam BigDecimal price, @RequestParam BigDecimal amount, Principal principal) throws Exception {
        return remoteTradingController.createOrder(tradingPairId, type, price, amount, principal);
    }

    @Transactional
    @Override
    @RequestMapping(value = "/order/{orderId}/cancel", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("unchecked")
    public void cancelOrder(@PathVariable long orderId, Principal principal) throws Exception {
        remoteTradingController.cancelOrder(orderId, principal);
    }
}
