package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.*;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.log.CryptoWithdrawHistory;
import com.springapp.cryptoexchange.utils.CacheCleaner;
import com.springapp.cryptoexchange.utils.Calculator;
import com.springapp.cryptoexchange.utils.LockManager;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import net.anotheria.idbasedlock.IdBasedLock;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@CommonsLog
public class DaemonManagerImpl implements DaemonManager {
    private final Map<Long, AbstractWallet> daemonMap = new ConcurrentHashMap<>();

    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    AccountManager accountManager;

    @Autowired
    LockManager lockManager;

    @Autowired
    FeeManager feeManager;

    @Autowired
    CacheCleaner cacheCleaner;

    public void setDaemonSettings(Daemon settings) {
        JsonRPC daemon = new JsonRPC(settings.getDaemonHost(), settings.getDaemonPort(), settings.getDaemonLogin(), settings.getDaemonPassword());
        AbstractWallet wallet = CryptoCoinWallet.getDefaultAccount(daemon);
        daemonMap.put(settings.getCurrency().getId(), wallet);
    }

    @Transactional
    public Daemon getDaemonSettings(@NonNull Currency currency) {
        Assert.isTrue(currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO), "Invalid currency type");
        Session session = sessionFactory.getCurrentSession();
        return (Daemon) session.createCriteria(Daemon.class)
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000) // Hourly reload
    @Transactional
    public synchronized void loadDaemons() {
        log.info("Loading daemon settings...");
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) if(currency.isEnabled() && currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
            try {
                Daemon settings = getDaemonSettings(currency);
                Assert.notNull(settings, "Daemon settings not found");
                setDaemonSettings(settings);
            } catch (Exception e) {
                e.printStackTrace();
                log.error(e);
            }
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000) // Every 5m
    @Transactional
    public synchronized void loadTransactions() throws Exception {
        log.info("Reloading transactions...");
        List<Currency> currencyList = settingsManager.getCurrencyList();
        boolean hasErrors = false;
        for(Currency currency : currencyList) if(currency.isEnabled() && currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
            try {
                final AbstractWallet wallet = daemonMap.get(currency.getId());
                Assert.notNull(wallet, String.format("Daemon settings not found for currency: %s", currency));
                wallet.loadTransactions(100);
            } catch (Exception exc) {
                exc.printStackTrace();
                log.error(exc);
                hasErrors = true;
            }
        }
        if (!hasErrors) {
            cacheCleaner.cryptoBalanceEvict(); // Only clear cache if no errors
            log.info("Crypto-balance cache flushed");
        } else {
            log.fatal("Cannot reload transactions, fallback to cache");
            loadDaemons(); // try to fix
        }
    }

    public AbstractWallet getAccount(Currency currency) {
        Assert.isTrue(currency.isEnabled(), "Currency disabled");
        AbstractWallet wallet = daemonMap.get(currency.getId());
        Assert.notNull(wallet, "Daemon not found");
        return wallet;
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

    @SuppressWarnings("unchecked")
    public Set getAddressSet(@NonNull VirtualWallet virtualWallet) {
        final List<Address> addressList = getAddressList(virtualWallet);
        final Set strings = new HashSet();
        for (Address address : addressList) {
            strings.add(address.getAddress());
        }
        return strings;
    }

    @Cacheable(value = "getCryptoBalance", key = "#virtualWallet.id")
    public BigDecimal getCryptoBalance(@NonNull VirtualWallet virtualWallet) throws Exception {
        try {
            final AbstractWallet wallet = getAccount(virtualWallet.getCurrency());
            return wallet.summaryConfirmedBalance(getAddressSet(virtualWallet));
        } catch (Exception e) {
            e.printStackTrace();
            log.fatal(e);
            throw e;
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public List<com.bitcoin.daemon.Address.Transaction> getWalletTransactions(@NonNull VirtualWallet virtualWallet) throws Exception {
        final List<com.bitcoin.daemon.Address.Transaction> transactionList = new ArrayList<>();
        if(!virtualWallet.getCurrency().isEnabled()) {
            return transactionList; // empty
        }
        final AbstractWallet daemon = getAccount(virtualWallet.getCurrency());

        // Deposit:
        transactionList.addAll(daemon.getTransactions(getAddressSet(virtualWallet)));

        // Withdrawals
        Session session = sessionFactory.getCurrentSession();
        final List<CryptoWithdrawHistory> withdrawHistoryList = session.createCriteria(CryptoWithdrawHistory.class)
                .add(Restrictions.eq("sourceWallet", virtualWallet))
                .list();
        for(CryptoWithdrawHistory withdrawHistory : withdrawHistoryList) {
            transactionList.add(daemon.getTransaction(withdrawHistory.getTransaction().getTxid()));
        }

        return transactionList;
    }

    @Transactional
    public com.bitcoin.daemon.Address.Transaction withdrawFunds(@NonNull VirtualWallet wallet, @NonNull String address, @NonNull BigDecimal amount) throws Exception {
        Assert.isTrue(wallet.getCurrency().getCurrencyType().equals(Currency.CurrencyType.CRYPTO) && address.length() > 0 && amount.compareTo(BigDecimal.ZERO) > 0, "Invalid parameters");
        BigDecimal minAmount = wallet.getCurrency().getMinimalWithdrawAmount();
        Assert.isTrue(amount.compareTo(minAmount) >= 0, String.format("Minimal withdraw amount: %s %s", minAmount, wallet.getCurrency().getCurrencyCode()));
        Session session = sessionFactory.getCurrentSession();
        IdBasedLock<Long> lock = lockManager.getCurrencyLockManager().obtainLock(wallet.getCurrency().getId()); // Critical
        lock.lock();
        try {
            session.refresh(wallet);
            CryptoCoinWallet.Account account = (CryptoCoinWallet.Account) getAccount(wallet.getCurrency());
            BigDecimal balance = accountManager.getVirtualWalletBalance(wallet);
            BigDecimal required = Calculator.withFee(amount, wallet.getCurrency().getWithdrawFee());
            if(balance.compareTo(required) < 0 || account.summaryConfirmedBalance().compareTo(required) < 0) {
                // throw new AccountManager.AccountException("Crypto-wallet account has insufficient funds");
            }
            wallet.addBalance(required.negate());
            session.update(wallet);

            log.info(String.format("Funds withdraw requested: from %s to %s <%s>", wallet, address, amount));
            com.bitcoin.daemon.Address.Transaction transaction = account.sendToAddress(address, amount);
            try {
                log.info(String.format("Funds withdraw success: %s", transaction));

                feeManager.submitCollectedFee(FreeBalance.FeeType.WITHDRAW, wallet.getCurrency(), Calculator.fee(amount, wallet.getCurrency().getWithdrawFee()).add(transaction.getFee())); // Tx fee is negative

                CryptoWithdrawHistory withdrawHistory = new CryptoWithdrawHistory(wallet, transaction);
                session.save(withdrawHistory);
            } catch (Exception e) {
                e.printStackTrace();
                log.fatal("Error after transaction: " + e);
                // do not throw!
            }
            return transaction;
        } catch (Exception e) {
            e.printStackTrace();
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
