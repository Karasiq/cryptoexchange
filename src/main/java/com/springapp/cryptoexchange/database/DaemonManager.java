package com.springapp.cryptoexchange.database;

import com.bitcoin.daemon.AbstractWallet;
import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import com.bitcoin.daemon.TestingWallet;
import com.springapp.cryptoexchange.Calculator;
import com.springapp.cryptoexchange.database.model.Address;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.Daemon;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
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

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private Daemon getDaemonSettings(@NonNull Currency currency) {
        assert currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO);
        @Cleanup Session session = sessionFactory.openSession();
        return (Daemon) session.createCriteria(Daemon.class)
                .add(Restrictions.eq("currency", currency))
                .uniqueResult();
    }

    @Scheduled(fixedDelay = 1000 * 60 * 5) // Every 5m
    private synchronized void loadTransactions(final int max) throws Exception {
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) {
            final DaemonInfo daemonInfo = daemonMap.get(currency);
            if(daemonInfo.enabled && daemonInfo.wallet != null) {
                try {
                    daemonInfo.wallet.loadTransactions(max);
                }
                catch (JsonRPC.RPCDaemonException exc) {
                    log.error(exc);
                }
            }
        }
    }

    private synchronized void initDaemons() {
        List<Currency> currencyList = settingsManager.getCurrencyList();
        for(Currency currency : currencyList) {
            if(!daemonMap.containsKey(currency) && currency.getCurrencyType().equals(Currency.CurrencyType.CRYPTO)) {
                Daemon settings = getDaemonSettings(currency);
                JsonRPC daemon = new JsonRPC(settings.getDaemonHost(), settings.getDaemonPort(), settings.getDaemonLogin(), settings.getDaemonPassword());
                AbstractWallet wallet = settingsManager.isTestingMode() ? new TestingWallet() : CryptoCoinWallet.getDefaultAccount(daemon);
                daemonMap.put(currency, new DaemonInfo(wallet));
            }
        }
    }

    public AbstractWallet getAccount(Currency currency) {
        DaemonInfo daemonInfo = daemonMap.get(currency);
        assert daemonInfo.enabled;
        return daemonInfo.wallet;
    }

    @Transactional
    public String createWalletAddress(@NonNull VirtualWallet virtualWallet) throws Exception {
        assert !settingsManager.isTestingMode() && virtualWallet.getCurrency().getCurrencyType().equals(Currency.CurrencyType.CRYPTO);
        Session session = sessionFactory.getCurrentSession();
        session.saveOrUpdate(virtualWallet);
        CryptoCoinWallet.Account account = (CryptoCoinWallet.Account) getAccount(virtualWallet.getCurrency());
        com.bitcoin.daemon.Address newAddress = account.generateNewAddress();
        Address address = new Address(newAddress.getAddress(), virtualWallet);
        session.saveOrUpdate(address);
        return newAddress.getAddress();
    }

    @Transactional
    public void withdrawFunds(@NonNull VirtualWallet wallet, String address, BigDecimal amount) throws Exception {
        assert !settingsManager.isTestingMode() && wallet.getCurrency().getCurrencyType().equals(Currency.CurrencyType.CRYPTO);
        IdBasedLock<VirtualWallet> lock = lockManager.getVirtualWalletLockManager().obtainLock(wallet);
        lock.lock();
        try {
            CryptoCoinWallet.Account account = (CryptoCoinWallet.Account) getAccount(wallet.getCurrency());
            BigDecimal balance = accountManager.getVirtualWalletBalance(wallet);
            BigDecimal required = amount.multiply(Calculator.ONE_HUNDRED.add(wallet.getCurrency().getWithdrawFee()).divide(Calculator.ONE_HUNDRED, 8, RoundingMode.FLOOR));
            if(balance.compareTo(required) < 0 || account.summaryConfirmedBalance().compareTo(required) < 0) {
                throw new AbstractAccountManager.AccountException("Insufficient funds");
            }

            log.info(String.format("Funds withdraw requested: from %s to %s <%s>", wallet, address, amount));

            com.bitcoin.daemon.Address.Transaction transaction;
            try {
                transaction = account.sendToAddress(address, amount);
                log.info(String.format("Funds withdraw success: %s", transaction));
            } catch (Exception e) {
                log.error(e);
                throw new AbstractAccountManager.AccountException(e);
            }

            // if no errors:
            wallet.addBalance(required.negate());
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
        @Cleanup Session session = sessionFactory.openSession();
        initDaemons();
        loadTransactions(20000);
    }
}
