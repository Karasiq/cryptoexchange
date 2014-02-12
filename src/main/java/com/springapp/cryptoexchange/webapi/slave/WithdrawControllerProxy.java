package com.springapp.cryptoexchange.webapi.slave;

import com.springapp.cryptoexchange.webapi.ApiDefs;
import com.springapp.cryptoexchange.webapi.master.WithdrawController;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;

@Controller
@RequestMapping("/rest/withdraw.json")
@CommonsLog
@Secured("ROLE_USER")
@Profile("slave")
public class WithdrawControllerProxy implements WithdrawController {
    @Autowired
    WithdrawController remoteWithdrawController;

    @Override
    @RequestMapping(value = "/crypto/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ApiDefs.ApiStatus withdrawCrypto(@PathVariable long currencyId, @RequestParam String address, @RequestParam BigDecimal amount, Principal principal) {
        return remoteWithdrawController.withdrawCrypto(currencyId, address, amount, principal);
    }
}
