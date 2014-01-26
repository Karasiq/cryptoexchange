package com.springapp.cryptoexchange.database;


import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.springapp.cryptoexchange.Calculator;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.LoginHistory;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Transactional
@CommonsLog
public class AccountManager implements AbstractAccountManager {
    @Autowired
    AbstractDaemonManager daemonManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    public synchronized void withdrawFunds(VirtualWallet wallet, String address, BigDecimal amount) throws Exception {
        final AbstractWallet abstractWallet = daemonManager.getAccount(wallet.getCurrency());
        if(abstractWallet instanceof CryptoCoinWallet.Account) {
            CryptoCoinWallet.Account account = (CryptoCoinWallet.Account) abstractWallet;
            BigDecimal balance = wallet.getBalance(account);
            BigDecimal required = amount.multiply(Calculator.ONE_HUNDRED.add(wallet.getCurrency().getWithdrawFee()).divide(Calculator.ONE_HUNDRED, 8, RoundingMode.FLOOR));
            if(balance.compareTo(required) < 0 || account.summaryConfirmedBalance().compareTo(required) < 0) {
                throw new AccountException("Insufficient funds");
            }

            log.info(String.format("Funds withdraw requested: from %s to %s <%s>", wallet, address, amount));

            com.bitcoin.daemon.Address.Transaction transaction;
            try {
                transaction = account.sendToAddress(address, amount);
                log.info(String.format("Funds withdraw success: %s", transaction));
            } catch (Exception e) {
                log.error(e);
                throw new AccountException(e);
            }

            // if no errors:
            wallet.addBalance(required.negate());
        }
    }
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

    @Transactional
    public void login(String login, String password, String ip, String browserFingerprint) throws Exception {
        Account account = getAccount(login);
        if(account == null || !account.checkPassword(password) || !account.isEnabled()) {
            throw new AccountException("Invalid login/password");
        }
        LoginHistory loginHistory = new LoginHistory(ip, browserFingerprint, account);
        Session session = sessionFactory.getCurrentSession();
        session.save(loginHistory);
        log.info(String.format("%s logged in: %s", login, loginHistory));
    }
}
