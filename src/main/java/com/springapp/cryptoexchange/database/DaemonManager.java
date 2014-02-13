package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import com.springapp.cryptoexchange.utils.Calculator;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.utils.LockManager;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.apachecommons.CommonsLog;
import net.anotheria.idbasedlock.IdBasedLock;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
@CommonsLog
public class DaemonManager implements AbstractDaemonManager {
    @RequiredArgsConstructor
    private static class DaemonInfo {
        boolean enabled = true;
        @NonNull AbstractWallet wallet;
    }
    private final Map<Currency, DaemonInfo> daemonMap = new ConcurrentHashMap<>();

    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    AbstractSettingsManager settingsManager;

    @Autowired
    AbstractAccountManager accountManager;

    @Autowired
    LockManager lockManager;

    @Autowired
    AbstractFeeManager feeManager;

    private Daemon getDaemonSettings(Session session, @NonNull Currency currency) {
        Assert.isTrue(currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO), "Invalid currency type");
        return (Daemon) session.createCriteria(Daemon.class)
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000) // Every 5m
    public synchronized void loadTransactions() throws Exception {
        log.info("Reloading transactions...");
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) if(currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
            final DaemonInfo daemonInfo = daemonMap.get(currency);
            if(daemonInfo != null && daemonInfo.enabled && daemonInfo.wallet != null) {
                try {
                    daemonInfo.wallet.loadTransactions(1000);
                }
                catch (JsonRPC.RPCDaemonException exc) {
                    log.error(exc);
                }
            }
        }
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000) // Hourly reload
    public synchronized void loadDaemons() {
        log.info("Loading daemon settings...");
        @Cleanup Session session = sessionFactory.openSession();
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) {
            if(currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
                Daemon settings = getDaemonSettings(session, currency);
                Assert.notNull(settings, "Daemon settings not found");
                JsonRPC daemon = new JsonRPC(settings.getDaemonHost(), settings.getDaemonPort(), settings.getDaemonLogin(), settings.getDaemonPassword());
                AbstractWallet wallet = CryptoCoinWallet.getDefaultAccount(daemon);
                daemonMap.put(currency, new DaemonInfo(wallet));
            }
        }
    }

    public AbstractWallet getAccount(Currency currency) {
        DaemonInfo daemonInfo = daemonMap.get(currency);
        Assert.notNull(daemonInfo, "Daemon not found");
        Assert.isTrue(daemonInfo.enabled, "Daemon disabled");
        return daemonInfo.wallet;
    }

    @Transactional
    public String createWalletAddress(@NonNull VirtualWallet virtualWallet) throws Exception {
        Assert.isTrue(virtualWallet.getCurrency().getCurrencyType().equals(Currency.CurrencyType.CRYPTO), "Invalid currency type");
        Session session = sessionFactory.getCurrentSession();
        session.saveOrUpdate(virtualWallet);
        CryptoCoinWallet.Account account = (CryptoCoinWallet.Account) getAccount(virtualWallet.getCurrency());
        com.bitcoin.daemon.Address newAddress = account.generateNewAddress();
        Address address = new Address(newAddress.getAddress(), virtualWallet);
        session.saveOrUpdate(address);
        return newAddress.getAddress();
    }

    @Transactional
    public void withdrawFunds(@NonNull VirtualWallet wallet, @NonNull String address, @NonNull BigDecimal amount) throws Exception {
        Assert.isTrue(wallet.getCurrency().getCurrencyType().equals(Currency.CurrencyType.CRYPTO) && address.length() > 0 && amount.compareTo(BigDecimal.ZERO) > 0, "Invalid parameters");
        Session session = sessionFactory.getCurrentSession();
        IdBasedLock<Long> lock = lockManager.getCurrencyLockManager().obtainLock(wallet.getCurrency().getId()); // Critical
        lock.lock();
        try {
            session.refresh(wallet);
            CryptoCoinWallet.Account account = (CryptoCoinWallet.Account) getAccount(wallet.getCurrency());
            BigDecimal balance = accountManager.getVirtualWalletBalance(wallet);
            BigDecimal required = Calculator.withFee(amount, wallet.getCurrency().getWithdrawFee());
            if(balance.compareTo(required) < 0 || account.summaryConfirmedBalance().compareTo(required) < 0) {
                throw new AbstractAccountManager.AccountException("Insufficient funds");
            }
            wallet.addBalance(required.negate());
            session.update(wallet);

            log.info(String.format("Funds withdraw requested: from %s to %s <%s>", wallet, address, amount));
            com.bitcoin.daemon.Address.Transaction transaction = account.sendToAddress(address, amount);
            log.info(String.format("Funds withdraw success: %s", transaction));
            feeManager.submitCollectedFee(FreeBalance.FeeType.WITHDRAW, wallet.getCurrency(), Calculator.fee(amount, wallet.getCurrency().getWithdrawFee()).add(transaction.getFee())); // Tx fee is negative
        } catch (Exception e) {
            log.error(e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<Address> getAddressList(@NonNull VirtualWallet wallet) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Address.class)
                .add(Restrictions.eq("virtualWallet", wallet))
                .list();
    }

    @PostConstruct
    public void init() throws Exception {
        loadDaemons();
        loadTransactions();
    }
}
