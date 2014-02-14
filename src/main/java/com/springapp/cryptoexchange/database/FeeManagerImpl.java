package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.Address;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.FreeBalance;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import net.anotheria.idbasedlock.IdBasedLock;
import net.anotheria.idbasedlock.IdBasedLockManager;
import net.anotheria.idbasedlock.SafeIdBasedLockManager;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Repository
@CommonsLog
@Transactional
public class FeeManagerImpl implements FeeManager {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    DaemonManager daemonManager;

    private final IdBasedLockManager<Long> freeBalanceLockManager = new SafeIdBasedLockManager<>();

    private FreeBalance getFreeBalance(FreeBalance.FeeType type, @NonNull Currency currency) {
        Session session = sessionFactory.getCurrentSession();
        FreeBalance balance = (FreeBalance) session.createCriteria(FreeBalance.class)
                .add(Restrictions.eq("currency", currency))
                .add(Restrictions.eq("type", type))
                .uniqueResult();
        if(balance == null) {
            balance = new FreeBalance(currency, type);
            session.save(balance);
        }
        return balance;
    }

    @Transactional
    public void submitCollectedFee(FreeBalance.FeeType type, @NonNull Currency currency, BigDecimal feeAmount) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        FreeBalance freeBalance = getFreeBalance(type, currency);
        IdBasedLock<Long> lock = freeBalanceLockManager.obtainLock(freeBalance.getId());
        lock.lock();
        try {
            BigDecimal newTotal = freeBalance.getAmount();
            if(newTotal == null) {
                newTotal = BigDecimal.ZERO;
            }
            newTotal = newTotal.add(feeAmount);
            freeBalance.setAmount(newTotal);
            log.info(String.format("%s fee collected: %s %s (total: %s)", type, feeAmount, currency.getCurrencyCode(), newTotal));
            session.update(freeBalance);
        } catch (Exception e) {
            log.error(e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public BigDecimal getCollectedFee(FreeBalance.FeeType type, @NonNull Currency currency) {
        return getFreeBalance(type, currency).getAmount();
    }

    private void withdrawCrypto(Session session, @NonNull FreeBalance freeBalance, BigDecimal amount, String address) throws Exception {
        IdBasedLock<Long> lock = freeBalanceLockManager.obtainLock(freeBalance.getId());
        lock.lock();
        try {
            BigDecimal current = freeBalance.getAmount();
            assert current.compareTo(amount) >= 0;
            CryptoCoinWallet.Account cryptoCoinWallet = (CryptoCoinWallet.Account) daemonManager.getAccount(freeBalance.getCurrency());
            Address.Transaction transaction = cryptoCoinWallet.sendToAddress(address, amount);
            freeBalance.setAmount(current.add(transaction.getAmount()).add(transaction.getFee()));
            session.update(freeBalance);
            log.info(String.format("Fee withdraw success: %s => %s (%s)", amount, address, transaction));
        } catch (Exception e) {
            log.error(e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public void withdrawFee(FreeBalance.FeeType type, @NonNull Currency currency, BigDecimal amount, Object receiverInfo) throws Exception {
        assert receiverInfo instanceof String; // Address
        Session session = sessionFactory.getCurrentSession();
        FreeBalance freeBalance = getFreeBalance(type, currency);
        withdrawCrypto(session, freeBalance, amount, (String) receiverInfo);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void withdrawFee(@NonNull Currency currency, BigDecimal amount, Object receiverInfo) throws Exception {
        assert receiverInfo instanceof String; // Address
        Session session = sessionFactory.getCurrentSession();
        List<FreeBalance> freeBalanceList = session.createCriteria(FreeBalance.class)
                .add(Restrictions.eq("currency", currency))
                .list();
        BigDecimal remaining = amount;
        for(FreeBalance freeBalance : freeBalanceList) {
            BigDecimal current = freeBalance.getAmount(), toSend = BigDecimal.ZERO;
            switch (current.compareTo(remaining)) {
                case 0:
                case 1:
                    toSend = remaining;
                    break;
                case -1:
                    toSend = current;
                    break;
            }
            withdrawCrypto(session, freeBalance, toSend, (String) receiverInfo);
            remaining = remaining.subtract(toSend);
            if(remaining.compareTo(BigDecimal.ZERO) <= 0) break;
        }
        switch (remaining.compareTo(BigDecimal.ZERO)) {
            case 1:
                log.warn(String.format("Insufficient funds to complete withdrawal: %s %s remaining", remaining, currency.getCurrencyCode()));
                break;
            case -1:
                log.fatal(String.format("Remaining is negative: %s", remaining));
                break;
        }
    }
}
