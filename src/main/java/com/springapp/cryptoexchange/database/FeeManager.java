package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import lombok.extern.apachecommons.CommonsLog;
import net.anotheria.idbasedlock.IdBasedLock;
import net.anotheria.idbasedlock.IdBasedLockManager;
import net.anotheria.idbasedlock.SafeIdBasedLockManager;
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

    private final IdBasedLockManager<Currency> lockManager = new SafeIdBasedLockManager<>();

    @Transactional
    public void submitCollectedFee(Currency currency, BigDecimal feeAmount) {
        Session session = sessionFactory.getCurrentSession();
        session.refresh(currency);
        IdBasedLock<Currency> lock = lockManager.obtainLock(currency);
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
}
