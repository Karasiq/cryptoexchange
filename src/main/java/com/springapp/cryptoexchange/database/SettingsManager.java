package com.springapp.cryptoexchange.database;

import com.springapp.cryptoexchange.database.model.Account;
import com.springapp.cryptoexchange.database.model.Currency;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.NonNull;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional
public class SettingsManager {
    private static List<Currency> currencyList = null; // cache
    @Autowired
    SessionFactory sessionFactory;

    // Currencies:
    @SuppressWarnings("unchecked")
    public List<Currency> getCurrencyList() {
        if (currencyList == null) {
            Session session = sessionFactory.getCurrentSession();
            currencyList = session.createCriteria(Currency.class).list();
        }
        return currencyList;
    }
}
