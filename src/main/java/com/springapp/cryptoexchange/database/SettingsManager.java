package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.TradingPair;
import lombok.Getter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class SettingsManager implements AbstractSettingsManager {
    @Autowired
    SessionFactory sessionFactory;

    private @Getter final BigDecimal feePercent = new BigDecimal(0.2);  // just static
    private @Getter final BigDecimal withdrawFeePercent = new BigDecimal(3);
    private @Getter List<Currency> currencyList = null;
    private @Getter List<TradingPair> tradingPairs = null;
    private boolean testingMode = false;

    public void setTestingMode(boolean testingMode) {
        this.testingMode = testingMode;
    }
    public boolean getTestingMode() {
        return testingMode;
    }


    @Transactional
    @SuppressWarnings("unchecked")
    public synchronized void loadCurrencies() {
        Session session = sessionFactory.getCurrentSession();
        currencyList = session.createCriteria(Currency.class)
                .add(Restrictions.eq("enabled", true)).list();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public synchronized void loadTradingPairs() {
        Session session = sessionFactory.getCurrentSession();
        tradingPairs = session.createCriteria(TradingPair.class)
                .add(Restrictions.eq("enabled", true)).list();
    }

    public synchronized void init() {
        loadCurrencies();
        loadTradingPairs();
    }
}
