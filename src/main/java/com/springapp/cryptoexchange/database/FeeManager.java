package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.CryptoCoinWallet;
import com.springapp.cryptoexchange.database.model.Currency;
import lombok.extern.apachecommons.CommonsLog;
import net.anotheria.idbasedlock.IdBasedLock;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
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

    @Transactional
    public void submitCollectedFee(Currency currency, BigDecimal feeAmount) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        session.refresh(currency);
        IdBasedLock<Currency> lock = lockManager.getCurrencyLockManager().obtainLock(currency);
        lock.lock();
        try {
            BigDecimal newTotal = currency.getCollectedFee();
            if(newTotal == null) {
                newTotal = BigDecimal.ZERO;
            }
            newTotal = newTotal.add(feeAmount);
            currency.setCollectedFee(newTotal);
            log.info(String.format("[FeeManager] Fee collected: %s %s (total: %s)", feeAmount, currency.getCurrencyCode(), newTotal));
        } catch (Exception e) {
            log.fatal(e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public BigDecimal getCollectedFee(Currency currency) {
        Session session = sessionFactory.getCurrentSession();
        session.refresh(currency);
        return currency.getCollectedFee();
    }

    @Transactional
    public void withdrawFee(Currency currency, BigDecimal amount, Object receiverInfo) throws Exception {
        assert receiverInfo instanceof String; // Address
        Session session = sessionFactory.getCurrentSession();
        session.refresh(currency);
        IdBasedLock<Currency> lock = lockManager.getCurrencyLockManager().obtainLock(currency);
        lock.lock();
        try {
            BigDecimal current = currency.getCollectedFee();
            assert current.compareTo(amount) >= 0;
            String address = (String) receiverInfo;
            CryptoCoinWallet.Account cryptoCoinWallet = (CryptoCoinWallet.Account) daemonManager.getAccount(currency);
            cryptoCoinWallet.sendToAddress(address, amount);
            currency.setCollectedFee(current.subtract(amount));
        } catch (Exception e) {
            log.fatal(e);
            throw e;
        } finally {
            lock.unlock();
        }
    }
}
