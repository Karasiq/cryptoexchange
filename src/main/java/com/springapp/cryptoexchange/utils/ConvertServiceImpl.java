package com.springapp.cryptoexchange.utils;

import com.springapp.cryptoexchange.database.*;
import com.springapp.cryptoexchange.database.model.*;
import com.springapp.cryptoexchange.database.model.Order;
import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.*;
import org.hibernate.transform.Transformers;
import org.hibernate.type.DoubleType;
import org.hibernate.type.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CommonsLog
@Service
public class ConvertServiceImpl implements ConvertService { // Convert layer
    @Autowired
    HistoryManager historyManager;

    @Autowired
    SettingsManager settingsManager;

    @Autowired
    MarketManager marketManager;

    @Autowired
    @Lazy
    DaemonManager daemonManager;

    @Autowired
    AccountManager accountManager;

    @Autowired
    SessionFactory sessionFactory;

    @SuppressWarnings("unchecked")
    private List<Depth.Entry> makeDepth(TradingPair tradingPair, int depthSize, Order.Type orderType) {
        Session session = sessionFactory.getCurrentSession();
        return session.createQuery("select ord.price as price, sum(ord.amount - ord.completedAmount) as amount from Order ord where " +
                "tradingPair = :tradingPair and type = :type and status in :statuses group by price " +
                "order by price " + (orderType.equals(Order.Type.BUY) ? "desc" : "asc"))
                .setParameter("tradingPair", tradingPair)
                .setParameter("type", orderType)
                .setParameterList("statuses", Arrays.asList(Order.Status.OPEN, Order.Status.PARTIALLY_COMPLETED))
                .setMaxResults(depthSize)
                .setResultTransformer(Transformers.aliasToBean(Depth.Entry.class))
                .list();
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Depth createDepth(TradingPair tradingPair, final int depthSize) throws Exception {
        final long start = System.nanoTime();
        final Depth depth = new Depth();
        depth.buyOrders = makeDepth(tradingPair, depthSize, Order.Type.BUY);
        depth.sellOrders = makeDepth(tradingPair, depthSize, Order.Type.SELL);
        log.info(String.format("Depth generated in %d ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
        return depth;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<MarketHistory> createHistory(@NonNull Criteria criteria) throws Exception {
        return criteria.setProjection(Projections.projectionList()
                    .add(Projections.property("type"), "type")
                    .add(Projections.property("price"), "price")
                    .add(Projections.property("completedAmount"), "amount")
                    .add(Projections.property("closeDate"), "time"))
                .setResultTransformer(Transformers.aliasToBean(MarketHistory.class))
                .list();
    }

    @Transactional
    public AccountBalanceInfo createAccountBalanceInfo(final @NonNull Account account) throws Exception {
        final long start = System.nanoTime();
        sessionFactory.getCurrentSession().refresh(account);
        List<Currency> currencyList = settingsManager.getCurrencyList();
        final AccountBalanceInfo accountBalanceInfo = new AccountBalanceInfo();
        for(Currency currency : currencyList) {
            BigDecimal balance = BigDecimal.ZERO;
            String address = null;
            try {
                VirtualWallet wallet = accountManager.getVirtualWallet(account, currency);
                if(wallet != null) {
                    if(wallet.getCurrency().getType().equals(Currency.Type.CRYPTO)) {
                        List<Address> addressList = daemonManager.getAddressList(wallet);
                        if (!addressList.isEmpty()) address = addressList.get(0).getAddress();
                    }
                    balance = accountManager.getVirtualWalletBalance(wallet);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            accountBalanceInfo.add(currency, balance, address);
        }
        Collections.sort(accountBalanceInfo.getAccountBalances(), new Comparator<AccountBalanceInfo.AccountBalance>() {
            @Override
            public int compare(AccountBalanceInfo.AccountBalance o1, AccountBalanceInfo.AccountBalance o2) {
                return o1.getCurrency().getName().compareTo(o2.getCurrency().getName());
            }
        });
        log.info(String.format("Balance info generated in %d ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
        return accountBalanceInfo;
    }

    private void convertToHighChartsOHLC(Candle candle, Object[] result) {
        result[0] = candle.getOpenTime().getTime();
        result[1] = candle.getOpen();
        result[2] = candle.getHigh();
        result[3] = candle.getLow();
        result[4] = candle.getClose();
        result[5] = candle.getVolume();
    }

    @Override
    public Object[][] createHighChartsOHLCData(final @NonNull List<Candle> candleList) throws Exception {
        final long start = System.nanoTime();
        final int length = candleList.size();
        final Object[][] result = new Object[length][6];
        ExecutorService executorService = Executors.newCachedThreadPool();
        for(int i = 0; i < length; i++) {
            final int index = i;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    convertToHighChartsOHLC(candleList.get(length - index - 1), result[index]);
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        log.info(String.format("Chart data generated in %d ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
        return result;
    }
}
