package com.springapp.cryptoexchange.database;


import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.database.model.log.LoginHistory;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.List;


@Repository
@CommonsLog
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountManagerImpl implements AccountManager, UserDetailsService {
    @Autowired
    @Lazy
    DaemonManager daemonManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    SessionFactory sessionFactory;

    @Transactional
    public VirtualWallet getVirtualWallet(@NonNull Account account, @NonNull Currency currency) {
        Session session = sessionFactory.getCurrentSession();
        VirtualWallet v = (VirtualWallet) session.createCriteria(VirtualWallet.class)
                .add(Restrictions.eq("account", account))
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
        if (v == null) {
            v = new VirtualWallet(currency, account);
            session.save(v);
        }
        return v;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("all")
    public BigDecimal getVirtualWalletBalance(@NonNull VirtualWallet wallet) throws Exception {
        Currency currency = wallet.getCurrency();
        Assert.notNull(currency, "Invalid parameters");
        BigDecimal externalBalance = wallet.getExternalBalance();
        if(currency.isEnabled()) if (currency.isCrypto()) {
            externalBalance = daemonManager.getCryptoBalance(wallet);
        }
        return wallet.getVirtualBalance().add(externalBalance);
    }

    @Transactional
    public Account addAccount(@NonNull Account account) {
        Session session = sessionFactory.getCurrentSession();
        session.save(account);
        log.info(String.format("New account registered: %s", account));
        return account;
    }

    @Transactional(readOnly = true)
    public Account getAccount(String login) {
        Session session = sessionFactory.getCurrentSession();
        return (Account) session.createCriteria(Account.class)
                .add(Restrictions.or(Restrictions.eq("login", login), Restrictions.eq("emailAddress", login)))
                .uniqueResult();
    }

    @Transactional
    public void logEntry(@NonNull Account account, String ip, String userAgentString) {
        Session session = sessionFactory.getCurrentSession();
        LoginHistory loginHistory = new LoginHistory(account, ip, userAgentString);
        session.save(loginHistory);
        log.info("Authenticated: " + loginHistory);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<LoginHistory> getLastEntriesByAccount(@NonNull Account account, int maxDaysAgo, int max) {
        Session session = sessionFactory.getCurrentSession();
        return (List<LoginHistory>) session.createCriteria(LoginHistory.class)
                .setMaxResults(max)
                .addOrder(org.hibernate.criterion.Order.desc("time"))
                .add(Restrictions.eq("account", account))
                .add(Restrictions.ge("time", DateTime.now().minus(Period.days(maxDaysAgo)).toDate()))
                .list();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<LoginHistory> getLastEntriesByIp(String ip, int maxDaysAgo, int max) {
        Session session = sessionFactory.getCurrentSession();
        return (List<LoginHistory>) session.createCriteria(LoginHistory.class)
                .setMaxResults(max)
                .addOrder(org.hibernate.criterion.Order.desc("time"))
                .add(Restrictions.eq("ip", ip))
                .add(Restrictions.ge("time", DateTime.now().minus(Period.days(maxDaysAgo)).toDate()))
                .list();
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account user = getAccount(username);
        if (user != null) {
            // User(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities)
            return new User(user.getLogin(), user.getPasswordHash(), user.isEnabled(), true, true, user.isEnabled(), user.getRole().getGrantedAuthorities());
        } else {
            throw new UsernameNotFoundException(String.format("No user with username '%s' found", username));
        }
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Order> getAccountOrders(@NonNull Account account, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .add(Restrictions.not(Restrictions.eq("status", Order.Status.CANCELLED)))
                .setMaxResults(max)
                .add(Restrictions.eq("account", account))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"))
                .list();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Order> getAccountOrdersByPair(@NonNull TradingPair tradingPair, @NonNull Account account, int max) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Order.class)
                .setMaxResults(max)
                .add(Restrictions.not(Restrictions.eq("status", Order.Status.CANCELLED)))
                .add(Restrictions.eq("tradingPair", tradingPair))
                .add(Restrictions.eq("account", account))
                .addOrder(org.hibernate.criterion.Order.desc("updateDate"))
                .list();
    }

    @Transactional
    @Scheduled(cron = "30 4 * * 1 *") // Sunday 4:30
    public void entryLogAutoClean() {
        log.info("Login history auto-clean started");
        Session session = sessionFactory.getCurrentSession();
        int affectedRows = session.createQuery("delete from LoginHistory where time <= :time")
                .setDate("time", DateTime.now().minus(Period.weeks(1)).toDate())
                .executeUpdate();
        log.info(String.format("Entries deleted: %d", affectedRows));
    }
}
