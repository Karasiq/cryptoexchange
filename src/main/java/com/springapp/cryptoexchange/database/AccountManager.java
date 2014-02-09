package com.springapp.cryptoexchange.database;


import com.bitcoin.daemon.AbstractWallet;
import com.springapp.cryptoexchange.database.model.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import net.anotheria.idbasedlock.IdBasedLock;
import net.anotheria.idbasedlock.IdBasedLockManager;
import net.anotheria.idbasedlock.SafeIdBasedLockManager;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletRequest;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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

    private final IdBasedLockManager<Long> virtualWalletLockManager = new SafeIdBasedLockManager<>();
    @Bean IdBasedLockManager<Long> getVirtualWalletLockManager() {
        return virtualWalletLockManager;
    }

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

    private BigDecimal getCryptoBalance(VirtualWallet virtualWallet) throws Exception {
        final AbstractWallet wallet = daemonManager.getAccount(virtualWallet.getCurrency());
        final List<Address> addressList = daemonManager.getAddressList(virtualWallet);
        if(!addressList.isEmpty()) {
            Set<Object> strings = new HashSet<>();
            for(Address address : addressList) {
                strings.add(address.getAddress());
            }
            return wallet.summaryConfirmedBalance(strings);
        } else {
            return BigDecimal.ZERO;
        }
    }

    @Transactional
    @Override
    public BigDecimal getVirtualWalletBalance(VirtualWallet wallet) throws Exception {
        BigDecimal resultBalance = wallet.getVirtualBalance();
        IdBasedLock<Long> lock = virtualWalletLockManager.obtainLock(wallet.getId());
        lock.lock();
        try {
            Currency currency = wallet.getCurrency();
            switch (currency.getCurrencyType()) {
                case CRYPTO:
                    resultBalance = resultBalance.add(getCryptoBalance(wallet));
                    break;
                case PURE_VIRTUAL:
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            log.error(e);
            throw e;
        } finally {
            lock.unlock();
        }
        return resultBalance;
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

    @Transactional
    public Account getAccount(String login) {
        return (Account) sessionFactory.getCurrentSession().createCriteria(Account.class)
                .add(Restrictions.or(Restrictions.eq("login", login), Restrictions.eq("login", login)))
                .uniqueResult();
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
