package com.springapp.cryptoexchange.database;

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

    private FreeBalance getFreeBalance(Session session, @NonNull Currency currency) {
        FreeBalance balance = (FreeBalance) session.createCriteria(FreeBalance.class)
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
        if(balance == null) {
            balance = new FreeBalance(currency);
            session.save(balance);
        }
        return balance;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public void submitCollectedFee(FreeBalance.FeeType type, @NonNull Currency currency, @NonNull BigDecimal feeAmount) throws Exception {
        if (feeAmount.equals(BigDecimal.ZERO)) {
            return;
        }
        Session session = sessionFactory.getCurrentSession();
        FreeBalance freeBalance = getFreeBalance(session, currency);
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
            log.fatal(e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getCollectedFee(@NonNull Currency currency) {
        return getFreeBalance(sessionFactory.getCurrentSession(), currency).getAmount();
    }

    private Address.Transaction withdrawCrypto(Session session, FreeBalance freeBalance, BigDecimal amount, String address) throws Exception {
        final BigDecimal current = freeBalance.getAmount();
        Assert.isTrue(current.compareTo(amount) >= 0, "Insufficient funds");
        CryptoCoinWallet.Account cryptoCoinWallet = (CryptoCoinWallet.Account) daemonManager.getAccount(freeBalance.getCurrency());
        Address.Transaction transaction = cryptoCoinWallet.sendToAddress(address, amount);
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
    public Object withdrawFee(@NonNull Currency currency, @NonNull BigDecimal amount, @NonNull Object receiverInfo) throws Exception {
        Assert.isTrue(currency.isEnabled(), "Currency disabled");
        Session session = sessionFactory.getCurrentSession();
        FreeBalance freeBalance = getFreeBalance(session, currency);
        Assert.notNull(freeBalance, "Balance not found");
        if (receiverInfo instanceof VirtualWallet) { // Virtual transaction
            withdrawInternal(session, freeBalance, (VirtualWallet) receiverInfo, amount);
            return null;
        } else switch (currency.getCurrencyType()) {
            case CRYPTO:
                Assert.isInstanceOf(String.class, receiverInfo, "Invalid address");
                return withdrawCrypto(session, freeBalance, amount, (String) receiverInfo);
            default:
                throw new IllegalArgumentException("Invalid currency type");
        }
    }

    @Profile("master")
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    @Scheduled(cron = "30 5 * * 2 *") // Monday 5:30
    public void calculateDivergence() throws Exception {
        Session session = sessionFactory.getCurrentSession();
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) if(currency.isEnabled() && !currency.getCurrencyType().equals(Currency.CurrencyType.PURE_VIRTUAL)) {
            BigDecimal overallBalance, databaseBalance = BigDecimal.ZERO;
            switch (currency.getCurrencyType()) {
                case CRYPTO:
                    AbstractWallet account = daemonManager.getAccount(currency);
                    overallBalance = account.summaryConfirmedBalance();
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            List<VirtualWallet> virtualWalletList = session.createCriteria(VirtualWallet.class)
                    .add(Restrictions.eq("currency", currency))
                    .list();
            for (VirtualWallet virtualWallet : virtualWalletList) {
                databaseBalance = databaseBalance.add(accountManager.getVirtualWalletBalance(virtualWallet));
            }
            databaseBalance = databaseBalance.add(getFreeBalance(session, currency).getAmount());
            if(!overallBalance.equals(databaseBalance)) {
                BigDecimal divergence = overallBalance.subtract(databaseBalance);
                log.fatal(String.format("Balance divergence for currency %s: EXTERNAL=%s, PERSISTED=%s, DIVERGENCE=%s", currency, overallBalance, databaseBalance, divergence));
                // submitCollectedFee(FreeBalance.FeeType.CORRECTION, currency, divergence);
            }
        }
    }
}
