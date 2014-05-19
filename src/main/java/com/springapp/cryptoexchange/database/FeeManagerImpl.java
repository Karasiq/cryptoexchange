package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.AbstractTransaction;
import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.Address;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.FreeBalance;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.FetchMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
@CommonsLog
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FeeManagerImpl implements FeeManager {
    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    @Lazy
    DaemonManager daemonManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    AccountManager accountManager;

    private FreeBalance getFreeBalance(Session session, Currency currency) {
        FreeBalance balance = (FreeBalance) session.createCriteria(FreeBalance.class)
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
        if(balance == null) {
            balance = new FreeBalance(currency);
            session.save(balance);
        }
        return balance;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<FreeBalance> getFreeBalances() {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(FreeBalance.class)
                .setFetchSize(10)
                .setFetchMode("currency", FetchMode.JOIN)
                .list();
    }

    @Transactional(readOnly = true)
    public FreeBalance getFreeBalance(@NonNull Currency currency) {
        return getFreeBalance(sessionFactory.getCurrentSession(), currency);
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    public void submitCollectedFee(FreeBalance.FeeType type, @NonNull Currency currency, @NonNull BigDecimal feeAmount) throws Exception {
        if (feeAmount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        Session session = sessionFactory.getCurrentSession();
        FreeBalance freeBalance = getFreeBalance(session, currency);
        try {
            BigDecimal newTotal = freeBalance.getAmount();
            newTotal = newTotal == null ? feeAmount : newTotal.add(feeAmount);
            freeBalance.setAmount(newTotal);
            log.info(String.format("%s fee collected: %s %s (total: %s)", type, feeAmount, currency.getCode(), newTotal));
            session.update(freeBalance);
        } catch (Exception e) {
            log.fatal(e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getCollectedFee(@NonNull Currency currency) {
        return getFreeBalance(sessionFactory.getCurrentSession(), currency).getAmount();
    }

    private AbstractTransaction withdrawCrypto(Session session, FreeBalance freeBalance, BigDecimal amount, String address) throws Exception {
        final BigDecimal current = freeBalance.getAmount();
        Assert.isTrue(current.compareTo(amount) >= 0, "Insufficient funds");
        CryptoCoinWallet cryptoCoinWallet = (CryptoCoinWallet) daemonManager.getAccount(freeBalance.getCurrency());
        AbstractTransaction transaction = cryptoCoinWallet.sendToAddress(address, amount);
        freeBalance.setAmount(current.subtract(amount).add(transaction.getFee()));
        session.update(freeBalance);
        log.info(String.format("Fee withdraw success: %s => %s (%s)", amount, address, transaction));
        return transaction;
    }

    private void withdrawInternal(Session session, FreeBalance freeBalance, VirtualWallet virtualWallet, BigDecimal amount) {
        BigDecimal current = freeBalance.getAmount();
        Assert.isTrue(freeBalance.getCurrency().equals(virtualWallet.getCurrency()), "Invalid wallet");
        Assert.isTrue(current.compareTo(amount) >= 0, "Insufficient funds");
        virtualWallet.addBalance(amount);
        freeBalance.setAmount(current.subtract(amount));
        session.update(virtualWallet);
        session.update(freeBalance);
        log.info(String.format("Internal fee withdraw success: %s => %s", amount, virtualWallet));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    public Object withdrawFee(@NonNull Currency currency, @NonNull BigDecimal amount, @NonNull Object receiverInfo) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        FreeBalance freeBalance = getFreeBalance(session, currency);
        Assert.notNull(freeBalance, "Balance not found");
        if (receiverInfo instanceof VirtualWallet) { // Virtual transaction
            withdrawInternal(session, freeBalance, (VirtualWallet) receiverInfo, amount);
            return null;
        } else if (currency.isCrypto()) {
            Assert.isTrue(currency.isEnabled(), "Currency disabled");
            Assert.isInstanceOf(String.class, receiverInfo, "Invalid address");
            return withdrawCrypto(session, freeBalance, amount, (String) receiverInfo);
        } else throw new IllegalArgumentException("Unknown currency type");
    }

    @Profile("master")
    @SuppressWarnings("unchecked")
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    @Scheduled(cron = "30 5 * * 2 *") // Monday 5:30
    public void calculateDivergence() throws Exception {
        long startTime = System.nanoTime();
        Session session = sessionFactory.getCurrentSession();
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) if(currency.isEnabled() && !currency.getType().equals(Currency.Type.PURE_VIRTUAL)) {
            BigDecimal overallBalance, databaseBalance = BigDecimal.ZERO;
            if (currency.isCrypto()) {
                AbstractWallet account = daemonManager.getAccount(currency);
                overallBalance = account.summaryConfirmedBalance();
            } else throw new IllegalArgumentException("Unknown currency type");
            final List<VirtualWallet> virtualWalletList = session.createCriteria(VirtualWallet.class)
                    .add(Restrictions.eq("currency", currency))
                    .list();
            for (VirtualWallet virtualWallet : virtualWalletList) {
                databaseBalance = databaseBalance.add(accountManager.getVirtualWalletBalance(virtualWallet));
            }
            FreeBalance freeBalance = getFreeBalance(session, currency);
            databaseBalance = databaseBalance.add(freeBalance.getAmount());
            if(!overallBalance.equals(databaseBalance)) {
                final BigDecimal divergence = overallBalance.subtract(databaseBalance),
                        actualFreeBalance = freeBalance.getAmount().add(divergence);
                log.fatal(String.format("Balance divergence for currency %s: EXTERNAL=%s, PERSISTED=%s, DIVERGENCE=%s, ACTUAL FREE=%s", currency, overallBalance, databaseBalance, divergence, actualFreeBalance));
                freeBalance.setAmount(actualFreeBalance);
                session.update(freeBalance);
            }
        }
        log.info(String.format("Free balance divergence calculated in %d ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)));
    }
}
