package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.Address;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import com.springapp.cryptoexchange.database.txlog.CryptoTransaction;
import com.springapp.cryptoexchange.database.txlog.InternalTransaction;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;

@Repository
@FieldDefaults(level = AccessLevel.PRIVATE)
@Profile("transaction-log")
public class TransactionLogManagerImpl implements TransactionLogManager { // Not required
    @Autowired
    SessionFactory sessionFactory;

    @Override
    @Async
    @Transactional
    public void addCryptoTransaction(@NonNull Address.Transaction transaction) {
        Assert.isTrue(transaction.isConfirmed(), "Transaction not confirmed");
        Session session = sessionFactory.getCurrentSession();
        com.springapp.cryptoexchange.database.model.Address address = (com.springapp.cryptoexchange.database.model.Address) session.createCriteria(com.springapp.cryptoexchange.database.model.Address.class).add(Restrictions.eq("address", transaction.getAddress())).uniqueResult();
        Assert.notNull(address, "Address not found");
        CryptoTransaction cryptoTransaction = new CryptoTransaction(transaction, address);
        session.saveOrUpdate(cryptoTransaction);
    }

    @Override
    @Async
    @Transactional
    public void addInternalTransaction(@NonNull VirtualWallet source, @NonNull VirtualWallet dest, @NonNull BigDecimal amount) {
        Assert.isTrue(!source.equals(dest) && !amount.equals(BigDecimal.ZERO), "Invalid arguments");
        Session session = sessionFactory.getCurrentSession();
        InternalTransaction internalTransaction = new InternalTransaction(source, dest, amount);
        session.save(internalTransaction);
    }
}
