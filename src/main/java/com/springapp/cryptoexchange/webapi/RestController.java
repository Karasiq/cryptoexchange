package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AbstractHistoryManager;
import com.springapp.cryptoexchange.database.AbstractMarketManager;
import com.springapp.cryptoexchange.database.AbstractSettingsManager;
import com.springapp.cryptoexchange.database.model.Order;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/rest-api")
public class RestController {
    public static class SessionInfo {
        public String loggedAs = "";
    }

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractMarketManager marketManager;

    @Autowired
    AbstractHistoryManager historyManager;

    @Autowired
    AbstractConvertService convertService;

    @RequestMapping(value = "/info")
    @ResponseBody
    List<TradingPair> getTradingPairs() {
        return settingsManager.getTradingPairs();
    }

    @RequestMapping(value = "/info/{tradingPairId}")
    @ResponseBody
    TradingPair getPrices(@PathVariable long tradingPairId) {
        return settingsManager.getTradingPair(tradingPairId);
    }

    @RequestMapping("/history/{tradingPairId}")
    @ResponseBody
    List<AbstractConvertService.MarketHistory> getMarketHistory(@PathVariable long tradingPairId) {
        return convertService.getMarketHistory(settingsManager.getTradingPair(tradingPairId));
    }

    @RequestMapping("/depth/{tradingPairId}")
    @ResponseBody
    AbstractConvertService.Depth getMarketDepth(@PathVariable long tradingPairId) {
        return convertService.getMarketDepth(settingsManager.getTradingPair(tradingPairId));
    }

    // Account:
    @RequestMapping(value = "/session", method = RequestMethod.GET)
    @ResponseBody
    SessionInfo getSessionInfo(@RequestHeader("X-Ajax-Call") boolean ajaxCall) {
        if(ajaxCall) {
            SessionInfo sessionInfo = new SessionInfo();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if(auth.isAuthenticated() && !auth.getName().equals("anonymous")) {
                sessionInfo.loggedAs = auth.getName();
            }
            return sessionInfo;
        }
        else throw new AccessDeniedException("You shouldn't call this method directly");
    }
}
