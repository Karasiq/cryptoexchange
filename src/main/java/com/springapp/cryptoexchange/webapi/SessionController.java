package com.springapp.cryptoexchange.webapi;

import com.bitcoin.daemon.CryptoCoinWallet;
import com.springapp.cryptoexchange.database.AbstractAccountManager;
import com.springapp.cryptoexchange.database.model.Account;
import lombok.Value;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@Controller
@RequestMapping(value = "/rest/login.json", headers = "X-Ajax-Call=true")
@CommonsLog
public class SessionController {
    @Autowired
    AbstractAccountManager accountManager;

    @Value
    public static class LoginStatus {
        private final boolean loggedIn;
        private final String username;
        private final String error;
    }

    @Value
    public static class RegisterStatus {
        private final boolean success;
        private final String error;
    }

    @Autowired
    AuthenticationManager authenticationManager;

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public LoginStatus getStatus() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !auth.getName().equals("anonymousUser") && auth.isAuthenticated()) {
            return new LoginStatus(true, auth.getName(), null);
        } else {
            return new LoginStatus(false, null, null);
        }
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public LoginStatus login(@RequestParam("j_username") String username, @RequestParam("j_password") String password) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(username, password);
        try {
            Authentication auth = authenticationManager.authenticate(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.info("Authenticated: " + username);
            return new LoginStatus(auth.isAuthenticated(), auth.getName(), null);
        } catch (Exception e) {
            log.warn(e);
            return new LoginStatus(false, null, e.getMessage());
        }
    }

    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public RegisterStatus register(@RequestParam String username, @RequestParam String password, @RequestParam String email) throws Exception {
        try {
            Account account = accountManager.getAccount(username);
            if(account != null) {
                return new RegisterStatus(false, "Username already taken");
            }
            account = accountManager.getAccount(email);
            if(account != null) {
                return new RegisterStatus(false, "E-mail already taken");
            }
            if(!Account.validate(username, password, email)) {
                return new RegisterStatus(false, "Bad user credentials");
            }
            accountManager.addAccount(new Account(username, email, password));
            login(username, password);
            return new RegisterStatus(true, null);
        } catch (Exception e) {
            log.warn(e);
            return new RegisterStatus(false, e.getMessage());
        }
    }
}