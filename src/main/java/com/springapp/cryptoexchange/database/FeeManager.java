package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.Address;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.FreeBalance;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import net.anotheria.idbasedlock.IdBasedLock;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@CommonsLog
@Transactional
public class FeeManager implements AbstractFeeManager {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AbstractDaemonManager daemonManager;

    @Autowired
    LockManager lockManager;

    private FreeBalance getFreeBalance(@NonNull Currency currency) {
        Session session = sessionFactory.getCurrentSession();
        FreeBalance balance = (FreeBalance) session.createCriteria(FreeBalance.class)
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
        if(balance == null) {
            balance = new FreeBalance(currency);
            session.save(balance);
        }
        return balance;
    }

    @Transactional
    public void submitCollectedFee(@NonNull Currency currency, BigDecimal feeAmount) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        FreeBalance freeBalance = getFreeBalance(currency);
        IdBasedLock<FreeBalance> lock = lockManager.getFreeBalanceLockManager().obtainLock(freeBalance);
        lock.lock();
        try {
            BigDecimal newTotal = freeBalance.getCollectedFee();
            if(newTotal == null) {
                newTotal = BigDecimal.ZERO;
            }
            newTotal = newTotal.add(feeAmount);
            freeBalance.setCollectedFee(newTotal);
            log.info(String.format("[FeeManager] Fee collected: %s %s (total: %s)", feeAmount, currency.getCurrencyCode(), newTotal));
            session.update(freeBalance);
        } catch (Exception e) {
            log.error(e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public BigDecimal getCollectedFee(@NonNull Currency currency) {
        return getFreeBalance(currency).getCollectedFee();
    }

    @Transactional
    public void withdrawFee(@NonNull Currency currency, BigDecimal amount, Object receiverInfo) throws Exception {
        assert receiverInfo instanceof String; // Address
        Session session = sessionFactory.getCurrentSession();
        FreeBalance freeBalance = getFreeBalance(currency);
        IdBasedLock<FreeBalance> lock = lockManager.getFreeBalanceLockManager().obtainLock(freeBalance);
        lock.lock();
        try {
            BigDecimal current = freeBalance.getCollectedFee();
            assert current.compareTo(amount) >= 0;
            String address = (String) receiverInfo;
            CryptoCoinWallet.Account cryptoCoinWallet = (CryptoCoinWallet.Account) daemonManager.getAccount(currency);
            Address.Transaction transaction = cryptoCoinWallet.sendToAddress(address, amount);
            freeBalance.setCollectedFee(current.add(transaction.getAmount()).add(transaction.getFee()));
            session.update(freeBalance);
        } catch (Exception e) {
            log.error(e);
            throw e;
        } finally {
            lock.unlock();
        }
    }
}
