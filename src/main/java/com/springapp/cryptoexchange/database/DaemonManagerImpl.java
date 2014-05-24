package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.*;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.*;
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
import org.hibernate.criterion.Projections;
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
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

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

    private static void closeWallet(DaemonInfo daemonInfo) {
        if (daemonInfo != null && daemonInfo.getWallet() != null) {
            AbstractWallet wallet = daemonInfo.getWallet();
            if (wallet instanceof Closeable) {
                try {
                    ((Closeable)wallet).close(); // Close connections
                } catch (IOException e) {
                    log.error(e);
                }
                log.info("Daemon connections closed: " + daemonInfo.getSettings().getCurrency());
            }
        }
    }

    private void produceDaemon(Daemon settings) {
        if (settings.getCurrency().getType().equals(Currency.Type.BITCOIN)) { // Generic
            JsonRPC daemon = new JsonRPC(settings.getDaemonHost(), settings.getDaemonPort(), settings.getDaemonLogin(), settings.getDaemonPassword());
            daemonMap.put(settings.getCurrency().getId(), new DaemonInfo(new BitcoinWallet(daemon), settings));
        } else throw new IllegalArgumentException("Unknown currency type");
        log.info("Daemon produced for currency: " + settings.getCurrency());
    }

    public synchronized void setDaemonSettings(Daemon settings) {
        long currencyId = settings.getCurrency().getId();
        DaemonInfo old = daemonMap.get(currencyId);
        if(old == null || !old.getSettings().equals(settings)) {
            closeWallet(old);
            produceDaemon(settings);
        }
    }

    @Transactional(readOnly = true)
    public Daemon getDaemonSettings(@NonNull Currency currency) {
        Assert.isTrue(currency.isCrypto(), "Invalid currency type");
        Session session = sessionFactory.getCurrentSession();
        return (Daemon) session.createCriteria(Daemon.class)
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
    }

    @Transactional(readOnly = true)
    public synchronized void loadDaemons() {
        log.debug("Loading daemon settings");
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) if (currency.isCrypto()) {
            if(currency.isEnabled()) {
                try {
                    Daemon settings = getDaemonSettings(currency);
                    Assert.notNull(settings, "Daemon settings not found");
                    setDaemonSettings(settings);
                } catch (Exception e) {
                    log.debug(e.getStackTrace());
                    log.error(e);
                }
            } else {
                DaemonInfo daemonInfo = daemonMap.get(currency.getId());
                if (daemonInfo != null) {
                    closeWallet(daemonInfo);
                    daemonMap.remove(currency.getId());
                }
            }
        }
    }

    @Scheduled(fixedDelay = 8 * 60 * 1000) // Every 8m
    @Transactional(readOnly = true)
    public synchronized void loadTransactions() throws Exception {
        loadDaemons();
        log.debug("Reloading transactions");
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) if(currency.isEnabled() && currency.isCrypto()) {
            try {
                DaemonInfo daemonInfo = daemonMap.get(currency.getId());
                Assert.notNull(daemonInfo, String.format("Daemon settings not found for currency: %s", currency));
                daemonInfo.getWallet().loadTransactions(300);
            } catch (DaemonRpcException exc) {
                log.error("Daemon error for currency: " + currency, exc);
            }
        }
        cacheCleaner.cryptoBalanceEvict();
    }

    public AbstractWallet getAccount(@NonNull Currency currency) {
        Assert.isTrue(currency.isEnabled(), "Currency disabled");
        Assert.isTrue(daemonMap.containsKey(currency.getId()), "Daemon not configured for currency: " + currency);
        AbstractWallet wallet = daemonMap.get(currency.getId()).getWallet();
        Assert.notNull(wallet, "Daemon not found");
        return wallet;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String createWalletAddress(@NonNull VirtualWallet virtualWallet) throws Exception {
        Assert.isTrue(virtualWallet.getCurrency().isCrypto(), "Invalid currency type");
        AbstractWallet account = getAccount(virtualWallet.getCurrency());
        if(account instanceof BitcoinWallet) { // Generic crypto-currency wallet
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

    @SuppressWarnings("unchecked")
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ)
    @Cacheable(value = "getCryptoBalance", key = "#virtualWallet.id")
    public BigDecimal getCryptoBalance(@NonNull VirtualWallet virtualWallet) throws Exception {
        try {
            Session session = sessionFactory.getCurrentSession();
            final AbstractWallet wallet = getAccount(virtualWallet.getCurrency());
            final BigDecimal externalBalance = wallet.summaryConfirmedBalance(getAddressSet(virtualWallet));
            if (virtualWallet.getExternalBalance().compareTo(externalBalance) != 0 && log.isInfoEnabled()) {
                BigDecimal difference = externalBalance.subtract(virtualWallet.getExternalBalance());
                log.info(String.format("External balance changed: %s (%s %s)", virtualWallet, difference, virtualWallet.getCurrency().getCode()));
            }
            virtualWallet.setExternalBalance(externalBalance); // rewrite
            session.update(virtualWallet);
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
    public <T extends AbstractTransaction> List<T> getWalletTransactions(@NonNull VirtualWallet virtualWallet) throws Exception {
        final Currency currency = virtualWallet.getCurrency();
        final List<T> transactionList = new ArrayList<>();
        if(!currency.isEnabled()) {
            return transactionList; // empty
        }
        final AbstractWallet daemon = getAccount(currency);

        // Deposit:
        transactionList.addAll(daemon.getTransactions(getAddressSet(virtualWallet)));

        // Withdrawals
        Session session = sessionFactory.getCurrentSession();
        final List<CryptoWithdrawHistory> withdrawHistoryList = session.createCriteria(CryptoWithdrawHistory.class)
                .add(Restrictions.eq("sourceWallet", virtualWallet))
                .add(Restrictions.ge("time", DateTime.now().minusDays(1).toDate()))
                .setMaxResults(3)
                .list();

        ExecutorService executorService = Executors.newCachedThreadPool();
        List<Future<T>> futureList = new ArrayList<>(withdrawHistoryList.size());
        for(final CryptoWithdrawHistory withdrawHistory : withdrawHistoryList) {
            // Refreshing transaction:
            futureList.add(executorService.submit(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    try {
                        AbstractTransaction transaction = daemon.getTransaction(withdrawHistory.getTransactionId());
                        if (transaction instanceof com.bitcoin.daemon.Address.Transaction) { // Fixes
                            final com.bitcoin.daemon.Address.Transaction btcTx = (com.bitcoin.daemon.Address.Transaction) transaction;
                            btcTx.setCategory("send");
                            btcTx.setAmount(withdrawHistory.getAmount());
                        }
                        return (T) transaction;
                    } catch (DaemonRpcException e) {
                        return withdrawHistory.transaction(currency.getType());
                    }
                }
            }));
        }
        for(Future<T> future : futureList) transactionList.add(future.get());
        executorService.shutdown();
        return transactionList;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
    public AbstractTransaction withdrawFunds(@NonNull VirtualWallet wallet, @NonNull String address, @NonNull BigDecimal amount) throws Exception {
        Session session = sessionFactory.getCurrentSession();
        Currency currency = wallet.getCurrency();
        Assert.isTrue(currency.isCrypto() && address.length() > 0 && amount.compareTo(BigDecimal.ZERO) > 0, "Invalid parameters");
        BigDecimal minAmount = currency.getMinimalWithdrawAmount();
        Assert.isTrue(amount.compareTo(minAmount) >= 0, String.format("Minimal withdraw amount: %s %s", minAmount, currency.getCode()));
        BigDecimal balance = accountManager.getVirtualWalletBalance(wallet), sendAmount = Calculator.withoutFee(amount, currency.getWithdrawFee());

        Assert.isTrue(balance.compareTo(amount) >= 0, "Insufficient funds");
        Assert.isTrue(((Number) session.createCriteria(CryptoWithdrawHistory.class)
                .add(Restrictions.eq("sourceWallet", wallet))
                .add(Restrictions.ge("time", DateTime.now().minusHours(1).toDate()))
                .setProjection(Projections.rowCount())
                .uniqueResult()).intValue() == 0, "Withdrawal frequency limited to once per hour for each currency.");

        wallet.addBalance(amount.negate());
        session.update(wallet);
        BitcoinWallet account = (BitcoinWallet) getAccount(currency);
        log.info(String.format("Funds withdraw requested: from %s to %s <%s>", wallet, address, amount));
        AbstractTransaction transaction = account.sendToAddress(address, sendAmount);
        try {
            log.info(String.format("Funds withdraw success: %s", transaction));

            BigDecimal withdrawFeeAmount = Calculator.fee(amount, currency.getWithdrawFee()),
                    transactionFeeAmount = transaction.getFee(),
                    summaryFeeAmount = withdrawFeeAmount.subtract(transactionFeeAmount);

            if(transactionFeeAmount.compareTo(BigDecimal.ZERO) < 0) {
                transactionFeeAmount = transactionFeeAmount.negate(); // Fix
            }

            log.info(String.format("Calculated withdraw fee: %s, transaction fee: %s, summary: %s",
                    withdrawFeeAmount, transactionFeeAmount, summaryFeeAmount));

            if (summaryFeeAmount.compareTo(BigDecimal.ZERO) < 0) // Negative
                log.fatal(String.format("Deficit: %s %s", summaryFeeAmount, currency.getCode()));

            feeManager.submitCollectedFee(FreeBalance.FeeType.WITHDRAW, currency, summaryFeeAmount);

            CryptoWithdrawHistory withdrawHistory = new CryptoWithdrawHistory(wallet, address, sendAmount, (String) transaction.getTxid());
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
        for(DaemonInfo daemonInfo : daemonMap.values()) closeWallet(daemonInfo);
        daemonMap.clear();
    }
}
