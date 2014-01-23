package com.springapp.cryptoexchange.database;


import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.NonNull;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AccountManager {
    @Autowired
    private SessionFactory sessionFactory;

    public boolean accountExists(Account account) {
        try {
            return getAccount(account.getLogin()) != null;
        } catch (Exception e) {
            return false;
        }
    }

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
    public String createWalletAddress(@NonNull VirtualWallet virtualWallet, @NonNull CryptoCoinWallet.Account account, @NonNull JsonRPC jsonRPC) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        session.update(virtualWallet);
        CryptoCoinWallet.Account.Address newAddress = account.generateNewAddress(jsonRPC);
        Address address = virtualWallet.addAddress(newAddress.getAddress());
        session.save(address);
        return newAddress.getAddress();
    }

    @Transactional
    public Account addAccount(Account account) {
        Session session = sessionFactory.getCurrentSession();
        session.save(account);
        return account;
    }

    @Transactional
    public void removeAccount(long id) {
        Session session = sessionFactory.getCurrentSession();
        Object account = session.createCriteria(Account.class).add(Restrictions.eq("id", id)).uniqueResult();
        session.delete(account);
    }

    public Account getAccount(String login) {
        return (Account) sessionFactory.getCurrentSession().createCriteria(Account.class).add(Restrictions.eq("login", login)).uniqueResult();
    }
}
