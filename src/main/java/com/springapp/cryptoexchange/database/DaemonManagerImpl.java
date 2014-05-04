package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.*;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.log.CryptoWithdrawHistory;
import com.springapp.cryptoexchange.utils.CacheCleaner;
import com.springapp.cryptoexchange.utils.Calculator;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@CommonsLog
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DaemonManagerImpl implements DaemonManager {
    @Value private static final class DaemonInfo {
        AbstractWallet wallet;
        Daemon settings;
    }
    final Map<Long, DaemonInfo> daemonMap = new ConcurrentHashMap<>();

    @Autowired
    SessionFactory sessionFactory;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    AccountManager accountManager;

    @Autowired
    FeeManager feeManager;

    @Autowired
    CacheCleaner cacheCleaner;

    public synchronized void setDaemonSettings(Daemon settings) {
        long currencyId = settings.getCurrency().getId();
        DaemonInfo old = daemonMap.get(currencyId);

        if(old == null || !old.getSettings().equals(settings)) {
            if (old != null) {
                AbstractWallet oldWallet = old.getWallet();
                if (oldWallet instanceof CryptoCoinWallet) {
                    ((CryptoCoinWallet)oldWallet).getJsonRPC().close(); // Close connections
                    log.info("Daemon connections closed: " + settings.getCurrency());
                }
            }
            JsonRPC daemon = new JsonRPC(settings.getDaemonHost(), settings.getDaemonPort(), settings.getDaemonLogin(), settings.getDaemonPassword());
            daemonMap.put(currencyId, new DaemonInfo(new CryptoCoinWallet(daemon), settings));
            log.info("Daemon settings changed for currency: " + settings.getCurrency());
        }
    }

    @Transactional(readOnly = true)
    public Daemon getDaemonSettings(@NonNull Currency currency) {
        Assert.isTrue(currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO), "Invalid currency type");
        Session session = sessionFactory.getCurrentSession();
        return (Daemon) session.createCriteria(Daemon.class)
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
    }

    @Transactional(readOnly = true)
    public synchronized void loadDaemons() {
        log.info("Loading daemon settings...");
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) if(currency.isEnabled() && currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
            try {
                Daemon settings = getDaemonSettings(currency);
                Assert.notNull(settings, "Daemon settings not found");
                setDaemonSettings(settings);
            } catch (Exception e) {
                log.debug(e.getStackTrace());
                log.error(e);
            }
        }
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000) // Every 10m
    @Transactional(readOnly = true)
    public synchronized void loadTransactions() throws Exception {
        loadDaemons();
        log.info("Reloading transactions...");
        List<Currency> currencyList = settingsManager.getCurrencyList();
        boolean hasErrors = false;
        for(Currency currency : currencyList) if(currency.isEnabled() && currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
            try {
                DaemonInfo daemonInfo = daemonMap.get(currency.getId());
                Assert.notNull(daemonInfo, String.format("Daemon settings not found for currency: %s", currency));
                daemonInfo.getWallet().loadTransactions(300);
            } catch (DaemonRpcException exc) {
                log.error("Daemon error for currency: " + currency, exc);
                hasErrors = true;
            }
        }
        cacheCleaner.cryptoBalanceEvict();
        /* if (!hasErrors) {
            cacheCleaner.cryptoBalanceEvict(); // Only clear cache if no errors
            log.info("Crypto-balance cache flushed");
        } else {
            log.fatal("Cannot reload transactions, fallback to cache");
            loadDaemons(); // try to fix
        } */
    }

    public AbstractWallet getAccount(Currency currency) {
        Assert.isTrue(currency.isEnabled(), "Currency disabled");
        AbstractWallet wallet = daemonMap.get(currency.getId()).getWallet();
        Assert.notNull(wallet, "Daemon not found");
        return wallet;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String createWalletAddress(@NonNull VirtualWallet virtualWallet) throws Exception {
        Assert.isTrue(virtualWallet.getCurrency().getCurrencyType().equals(Currency.CurrencyType.CRYPTO), "Invalid currency type");
        AbstractWallet account = getAccount(virtualWallet.getCurrency());
        if(account instanceof CryptoCoinWallet) { // Generic crypto-currency wallet
            String newAddress = (String) account.generateNewAddress();
            Address address = new Address(newAddress, virtualWallet);
            Session session = sessionFactory.getCurrentSession();
            session.save(address);
            return newAddress;
        } else throw new IllegalArgumentException("Unknown wallet type");
    }

    @Transactional(readOnly = true)
    public Set<String> getAddressSet(@NonNull VirtualWallet virtualWallet) {
        final List<Address> addressList = getAddressList(virtualWallet);
        final Set<String> strings = new HashSet<>();
        for (Address address : addressList) {
            strings.add(address.getAddress());
        }
        return strings;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Cacheable(value = "getCryptoBalance", key = "#virtualWallet.id")
    public BigDecimal getCryptoBalance(@NonNull VirtualWallet virtualWallet) throws Exception {
        try {
            final AbstractWallet wallet = getAccount(virtualWallet.getCurrency());
            final BigDecimal externalBalance = wallet.summaryConfirmedBalance(getAddressSet(virtualWallet));
            virtualWallet.setExternalBalance(externalBalance); // rewrite
            sessionFactory.getCurrentSession().update(virtualWallet);
        } catch(DaemonRpcException e) {
            log.error("Cannot retrieve actual crypto balance, fallback to DB-backup: " + virtualWallet.getCurrency(), e);
            // Just return DB backup, no throw
        } catch (Exception e) {
            log.debug(e.getStackTrace());
            log.fatal("getCryptoBalance unhandled exception", e);
            throw e;
        }
        return virtualWallet.getExternalBalance();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <T> List<T> getWalletTransactions(@NonNull VirtualWallet virtualWallet) throws Exception {
        final List<T> transactionList = new ArrayList<>();
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
                .add(Restrictions.ge("time", DateTime.now().minus(Period.weeks(1)).toDate()))
                .setMaxResults(10)
                .list();
        for(CryptoWithdrawHistory withdrawHistory : withdrawHistoryList) {
            // Refreshing transaction:
            final T transaction = (T) daemon.getTransaction(withdrawHistory.getTransactionId());

            if (transaction instanceof com.bitcoin.daemon.Address.Transaction) { // Fixes
                final com.bitcoin.daemon.Address.Transaction btcTx = (com.bitcoin.daemon.Address.Transaction) transaction;
                btcTx.setCategory("send");
                btcTx.setAmount(withdrawHistory.getAmount());
            }
            transactionList.add(transaction);
        }
        return transactionList;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    public com.bitcoin.daemon.Address.Transaction withdrawFunds(@NonNull VirtualWallet wallet, @NonNull String address, @NonNull BigDecimal amount) throws Exception {
        Currency currency = wallet.getCurrency();
        Assert.isTrue(currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO) && address.length() > 0 && amount.compareTo(BigDecimal.ZERO) > 0, "Invalid parameters");
        BigDecimal minAmount = currency.getMinimalWithdrawAmount();
        Assert.isTrue(amount.compareTo(minAmount) >= 0, String.format("Minimal withdraw amount: %s %s", minAmount, currency.getCurrencyCode()));
        Session session = sessionFactory.getCurrentSession();
        CryptoCoinWallet account = (CryptoCoinWallet) getAccount(currency);
        BigDecimal balance = accountManager.getVirtualWalletBalance(wallet), sendAmount = Calculator.withoutFee(amount, currency.getWithdrawFee());
        if(balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        wallet.addBalance(amount.negate());
        session.update(wallet);

        log.info(String.format("Funds withdraw requested: from %s to %s <%s>", wallet, address, amount));
        com.bitcoin.daemon.Address.Transaction transaction = account.sendToAddress(address, sendAmount);
        try {
            log.info(String.format("Funds withdraw success: %s", transaction));

            BigDecimal withdrawFeeAmount = Calculator.fee(amount, currency.getWithdrawFee()),
                    transactionFeeAmount = transaction.getFee().negate(),
                    summaryFeeAmount = withdrawFeeAmount.subtract(transactionFeeAmount);

            log.info(String.format("Calculated withdraw fee: %s, transaction fee: %s, summary: %s",
                    withdrawFeeAmount, transactionFeeAmount, summaryFeeAmount));

            if (summaryFeeAmount.compareTo(BigDecimal.ZERO) < 0) // Negative
                log.fatal(String.format("Deficit: %s %s", summaryFeeAmount, currency.getCurrencyCode()));

            feeManager.submitCollectedFee(FreeBalance.FeeType.WITHDRAW, currency, summaryFeeAmount);

            CryptoWithdrawHistory withdrawHistory = new CryptoWithdrawHistory(wallet, address, sendAmount, transaction.getTxid());
            session.save(withdrawHistory);
        } catch (Exception e) {
            log.debug(e.getStackTrace());
            log.fatal("Error after transaction", e);
            // do not throw!
        }
        return transaction;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Address> getAddressList(@NonNull VirtualWallet wallet) {
        Session session = sessionFactory.getCurrentSession();
        return session.createCriteria(Address.class)
                .add(Restrictions.eq("virtualWallet", wallet))
                .list();
    }

    @Autowired
    PlatformTransactionManager transactionManager;

    @PostConstruct
    public void init() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                try {
                    loadTransactions();
                } catch (Exception e) {
                    log.debug(e.getStackTrace());
                    log.error(e);
                }
            }
        });
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down JSON-RPC connections");
        for(DaemonInfo daemonInfo : daemonMap.values()) {
            AbstractWallet wallet = daemonInfo.getWallet();
            if (wallet instanceof CryptoCoinWallet) {
                ((CryptoCoinWallet)wallet).getJsonRPC().close(); // Close connections
            }
        }
        daemonMap.clear();
    }
}
