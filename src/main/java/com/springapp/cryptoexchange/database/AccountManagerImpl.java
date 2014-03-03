package com.springapp.cryptoexchange.database;


import com.bitcoin.daemon.AbstractWallet;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.utils.LockManager;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.*;


@Repository
@Transactional
@CommonsLog
public class AccountManagerImpl implements AccountManager, UserDetailsService {
    @Autowired
    DaemonManager daemonManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private LockManager lockManager;

    @Transactional
    public VirtualWallet getVirtualWallet(@NonNull Account account, @NonNull Currency currency) {
        Session session = sessionFactory.getCurrentSession();
        session.refresh(account);
        VirtualWallet v = account.getBalance(currency);
        if (v == null) {
            v = account.createVirtualWallet(currency);
            session.save(v);
        }
        return v;
    }

    @Cacheable(value = "getCryptoBalance", key = "#virtualWallet.id")
    private BigDecimal getCryptoBalance(VirtualWallet virtualWallet) throws Exception {
        try {
            final AbstractWallet wallet = daemonManager.getAccount(virtualWallet.getCurrency());
            final List<Address> addressList = daemonManager.getAddressList(virtualWallet);
            if (!addressList.isEmpty()) {
                Set<Object> strings = new HashSet<>();
                for (Address address : addressList) {
                    strings.add(address.getAddress());
                }
                return wallet.summaryConfirmedBalance(strings);
            } else {
                return BigDecimal.ZERO;
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.fatal(e);
            return BigDecimal.ZERO;
        }
    }

    @Transactional
    @Override
    @SuppressWarnings("all")
    public BigDecimal getVirtualWalletBalance(@NonNull VirtualWallet wallet) throws Exception {
        BigDecimal resultBalance = wallet.getVirtualBalance();
        Currency currency = wallet.getCurrency();
        Assert.isTrue(currency != null && currency.isEnabled(), "Invalid parameters");
        switch (currency.getCurrencyType()) {
            case CRYPTO:
                resultBalance = resultBalance.add(getCryptoBalance(wallet));
                break;
            case PURE_VIRTUAL:
                break;
            default:
                throw new IllegalArgumentException();
        }
        return resultBalance;
    }

    @Transactional
    public Account addAccount(@NonNull Account account) {
        Session session = sessionFactory.getCurrentSession();
        session.save(account);
        log.info(String.format("New account registered: %s", account));
        return account;
    }

    @Transactional
    public void setAccountEnabled(long id, boolean enabled) {
        Session session = sessionFactory.getCurrentSession();
        Account account = (Account) session.load(Account.class, id);
        account.setEnabled(enabled);
        session.update(account);
        log.info(String.format("Account modified: %s", account));
    }

    @Transactional
    public Account getAccount(String login) {
        return (Account) sessionFactory.getCurrentSession().createCriteria(Account.class)
                .add(Restrictions.or(Restrictions.eq("login", login), Restrictions.eq("emailAddress", login)))
                .uniqueResult();
    }

    @Transactional
    @Override
    public void logEntry(@NonNull Account account, String ip, String userAgentString) {
        Session session = sessionFactory.getCurrentSession();
        session.refresh(account);
        LoginHistory loginHistory = new LoginHistory(account, ip, userAgentString);
        session.save(loginHistory);
        log.info("Authenticated: " + loginHistory);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<LoginHistory> getLastEntriesByAccount(@NonNull Account account, int maxDaysAgo, int max) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, maxDaysAgo * -1);
        return (List<LoginHistory>) sessionFactory.getCurrentSession()
                .createCriteria(LoginHistory.class)
                .setMaxResults(max)
                .addOrder(org.hibernate.criterion.Order.desc("time"))
                .add(Restrictions.eq("account", account))
                .add(Restrictions.ge("time", calendar.getTime()))
                .list();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<LoginHistory> getLastEntriesByIp(String ip, int maxDaysAgo, int max) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, maxDaysAgo * -1);
        return (List<LoginHistory>) sessionFactory.getCurrentSession()
                .createCriteria(LoginHistory.class)
                .setMaxResults(max)
                .addOrder(org.hibernate.criterion.Order.desc("time"))
                .add(Restrictions.eq("ip", ip))
                .add(Restrictions.ge("time", calendar.getTime()))
                .list();
    }

    @Transactional
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account user = getAccount(username);
        if (user != null) {
            return new User(user.getLogin(), user.getPasswordHash(), user.isEnabled(), true, true, user.isEnabled(), user.getRole().getGrantedAuthorities());
        } else {
            throw new UsernameNotFoundException("No user with username '" + username + "' found!");
        }
    }

    @Transactional
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

    @Transactional
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
}
