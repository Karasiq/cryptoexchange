package com.springapp.cryptoexchange.database;


import com.springapp.cryptoexchange.database.model.*;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletRequest;
import java.util.Date;
import java.util.List;


@Service
@Transactional
@CommonsLog
public class AccountManager implements AbstractAccountManager, UserDetailsService {
    @Autowired
    AbstractDaemonManager daemonManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    private SessionFactory sessionFactory;

    @Transactional
    public VirtualWallet getVirtualWallet(@NonNull Account account, @NonNull Currency currency) {
        Session session = sessionFactory.getCurrentSession();
        session.refresh(account);
        VirtualWallet v = account.getBalance(currency);
        if(v == null) {
            v = account.createVirtualWallet(currency);
            session.save(v);
        }
        return v;
    }

    @Transactional
    public Account addAccount(Account account) {
        Session session = sessionFactory.getCurrentSession();
        session.save(account);
        log.info(String.format("New account registered: %s", account));
        return account;
    }

    @Transactional
    public void setAccountEnabled(long id, boolean enabled) {
        Session session = sessionFactory.getCurrentSession();
        Account account = (Account) session.createCriteria(Account.class).add(Restrictions.eq("id", id)).uniqueResult();
        account.setEnabled(enabled);
        log.info(String.format("Account modified: %s", account));
    }

    public Account getAccount(String login) {
        return (Account) sessionFactory.getCurrentSession().createCriteria(Account.class).add(Restrictions.or(Restrictions.eq("login", login), Restrictions.eq("login", login))).uniqueResult();
    }

   @Transactional
   private void logEntry(Account account) {
       ServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
       Session session = sessionFactory.getCurrentSession();
       session.refresh(account);
       LoginHistory loginHistory = new LoginHistory();
       if(request != null) {
           loginHistory.setIp(request.getRemoteAddr());
           // loginHistory.setFingerprint(request.getParameter("fingerprint"));
       }
       loginHistory.setAccount(account);
       loginHistory.setTime(new Date());
       session.save(loginHistory);
   }

    @Transactional
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account user = getAccount(username);
        if (user != null) {
            // logEntry(user);
            return new User(user.getLogin(), user.getPasswordHash(), user.isEnabled(), true, true, user.isEnabled(), user.getRole().getGrantedAuthorities());
        } else {
            throw new UsernameNotFoundException("No user with username '" + username + "' found!");
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Order> getAccountOrders(@NonNull Account account, int max) { // only for information!!!
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .setMaxResults(max)
                .add(Restrictions.eq("account", account))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"))
                .list();
    }
}
