package com.springapp.cryptoexchange.webapi.slave;

import com.bitcoin.daemon.Address;
import com.springapp.cryptoexchange.webapi.ApiDefs;
import com.springapp.cryptoexchange.webapi.master.WithdrawController;
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
@RequestMapping("/rest/withdraw.json")
@CommonsLog
@Secured("ROLE_USER")
@Profile("slave")
public class WithdrawControllerProxy implements WithdrawController {
    @Autowired
    WithdrawController remoteWithdrawController;

    @Transactional
    @Override
    @RequestMapping(value = "/crypto/{currencyId}", method = RequestMethod.POST)
    @ResponseBody
    @SuppressWarnings("unchecked")
    public Address.Transaction withdrawCrypto(@PathVariable long currencyId, @RequestParam String address, @RequestParam BigDecimal amount, Principal principal) throws Exception {
        return remoteWithdrawController.withdrawCrypto(currencyId, address, amount, principal);
    }
}
