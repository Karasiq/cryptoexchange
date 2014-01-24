package com.springapp.cryptoexchange.database;


import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.JsonRPC;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.NonNull;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
public class AccountManager implements AbstractAccountManager {
    private Log log = LogFactory.getLog(AccountManager.class);
    @Autowired
    AbstractDaemonManager daemonManager;

    @Autowired
    AbstractSettingsManager settingsManager;

    public synchronized com.bitcoin.daemon.Address.Transaction withdrawFunds(VirtualWallet wallet, String address, BigDecimal amount) throws Exception {
        final AbstractWallet account = daemonManager.getAccount(wallet.getCurrency());
        BigDecimal balance = wallet.getBalance(account);
        BigDecimal required = amount.multiply(new BigDecimal(100).add(settingsManager.getWithdrawFeePercent()).divide(new BigDecimal(100), 8, RoundingMode.FLOOR));
        if(balance.compareTo(required) < 0) {
            throw new AccountException("Insufficient funds");
        }

        log.info(String.format("Funds withdraw requested: from %s to %s <%s>", wallet, address, amount));

        com.bitcoin.daemon.Address.Transaction transaction;
        try {
            transaction = account.sendToAddress(daemonManager.getDaemon(wallet.getCurrency()), address, amount);
            log.info(String.format("Funds withdraw success: %s", transaction));
        } catch (Exception e) {
            log.error(e);
            throw new AccountException(e);
        }

        // if no errors:
        wallet.addBalance(required.negate());
        return transaction;
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
    public String createWalletAddress(@NonNull VirtualWallet virtualWallet, @NonNull AbstractWallet account, @NonNull JsonRPC jsonRPC) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        session.saveOrUpdate(virtualWallet);
        com.bitcoin.daemon.Address newAddress = account.generateNewAddress(jsonRPC);
        Address address = virtualWallet.addAddress(newAddress.getAddress());
        session.saveOrUpdate(address);
        return newAddress.getAddress();
    }

    @Transactional
    public Account addAccount(Account account) {
        Session session = sessionFactory.getCurrentSession();
        session.save(account);
        log.info(String.format("New account registered: %s", account));
        return account;
    }

    @Transactional
    public void removeAccount(long id) {
        Session session = sessionFactory.getCurrentSession();
        Object account = session.createCriteria(Account.class).add(Restrictions.eq("id", id)).uniqueResult();
        session.delete(account);
        log.info(String.format("Account removed: %s", account));
    }

    public Account getAccount(String login) {
        return (Account) sessionFactory.getCurrentSession().createCriteria(Account.class).add(Restrictions.eq("login", login)).uniqueResult();
    }
}
