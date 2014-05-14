package com.springapp.cryptoexchange.webapi;

import com.springapp.cryptoexchange.database.AccountManager;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.log.LoginHistory;
import com.springapp.cryptoexchange.utils.InetAddressUtil;
import lombok.AccessLevel;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.nio.file.AccessDeniedException;
import java.util.List;


@Controller
@RequestMapping(value = "/rest/login.json", headers = "X-Ajax-Call=true")
@CommonsLog
public class SessionController {
    @Autowired
    AccountManager accountManager;

    @Autowired
    SessionFactory sessionFactory;

    @Value
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class LoginStatus {
        boolean loggedIn;
        String username;
        String error;
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
    public LoginStatus login(@RequestParam("j_username") String username, @RequestParam("j_password") String password, @RequestParam(value = "two_factor_code", required = false, defaultValue = "0") int googleAuthCode, HttpServletRequest request) {
        try {
            Account account = accountManager.getAccount(username);
            Assert.notNull(account, "Account not found");
            account.checkGoogleAuth(googleAuthCode);
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(username, password);
            token.setDetails(new WebAuthenticationDetails(request));
            Authentication auth = authenticationManager.authenticate(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
            accountManager.logEntry(accountManager.getAccount(username), InetAddressUtil.getAddressFromRequest(request), request.getHeader("User-Agent"));
            return new LoginStatus(auth.isAuthenticated(), auth.getName(), null);
        } catch (Exception e) {
            log.debug(e.getStackTrace());
            log.error(e);
            return new LoginStatus(false, null, e.getMessage());
        }
    }

    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    @Transactional
    public LoginStatus register(@RequestParam String username, @RequestParam String password, @RequestParam String email, HttpServletRequest request) throws Exception {
        try {
            // Check if account already exists:
            Assert.isTrue(accountManager.getAccount(username) == null, "Username already taken");
            Assert.isTrue(accountManager.getAccount(email) == null, "E-mail already taken");

            // Check IP uniqueness:
            List<LoginHistory> loginHistories = accountManager.getLastEntriesByIp(request.getRemoteAddr(), 3, 1);
            if(loginHistories != null && !loginHistories.isEmpty()) {
                throw new AccessDeniedException("You cannot register multiply accounts from one IP");
            }

            // Create account:
            Account account = new Account(username, email, password);
            accountManager.addAccount(account);
            return login(username, password, 0, request);
        } catch (Exception e) {
            log.debug(e.getStackTrace());
            log.error(e);
            throw e;
        }
    }
}