package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AbstractAccountManager;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Order;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping(value = "/rest/account.json", headers = "X-Ajax-Call=true")
@CommonsLog
@Secured("ROLE_USER")
public class AccountController {
    @Autowired
    AbstractConvertService convertService;

    @Autowired
    AbstractAccountManager accountManager;

    @Cacheable(value = "getAccountBalanceInfo", key = "#principal.name")
    @RequestMapping("/balance")
    @ResponseBody
    public ApiDefs.ApiStatus<AbstractConvertService.AccountBalanceInfo> getAccountBalanceInfo(Principal principal) {
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert account != null; // Shouldn't happen
            return new ApiDefs.ApiStatus<>(true, null, convertService.createAccountBalanceInfo(account));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }

    @Cacheable(value = "getAccountBalanceInfo", key = "#principal.name")
    @RequestMapping("/orders")
    @ResponseBody
    public ApiDefs.ApiStatus<List<Order>> getAccountOpenOrders(Principal principal) {
        Account account = accountManager.getAccount(principal.getName());
        try {
            assert account != null; // Shouldn't happen
            return new ApiDefs.ApiStatus<>(true, null, accountManager.getAccountOrders(account, 200));
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e);
            return new ApiDefs.ApiStatus<>(false, e.getMessage(), null);
        }
    }
}
