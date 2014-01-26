package com.springapp.cryptoexchange.database;


import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
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
        session.update(account);
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
        return (Account) sessionFactory.getCurrentSession().createCriteria(Account.class).add(Restrictions.eq("login", login)).uniqueResult();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account user = getAccount(username);
        if (user != null) {
            return new User(user.getLogin(), user.getPasswordHash(), user.isEnabled(), false, false, !user.isEnabled(), user.getRole().getRoleClass().getGrantedAuthorities());
        } else {
            throw new UsernameNotFoundException("No user with username '" + username + "' found!");
        }
    }
}
