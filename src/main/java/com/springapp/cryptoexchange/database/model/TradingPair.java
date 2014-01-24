package com.springapp.cryptoexchange.database.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@NoArgsConstructor
@Table(name = "trading_pairs")
public class TradingPair {
    @Id
    @GeneratedValue
    @Column
    long id;

    @Column(name = "enabled")
    boolean enabled = true;

    @ManyToOne
    Currency firstCurrency;

    @ManyToOne
    Currency secondCurrency;

    @Column(name = "name")
    String name;

    @Column(name = "description")
    String description;

    @Column(name = "last_reset")
    Date lastReset = new Date();

    @Column(name = "volume")
    BigDecimal volume = BigDecimal.ZERO;

    @Column(name = "last_price")
    BigDecimal lastPrice = BigDecimal.ZERO;

    @Column(name = "high_price")
    BigDecimal dayHigh = BigDecimal.ZERO;

    @Column(name = "low_price")
    BigDecimal dayLow = BigDecimal.ZERO;

    public TradingPair(Currency firstCurrency, Currency secondCurrency) {
        setName(String.format("%s/%s", firstCurrency.getCurrencyCode(), secondCurrency.getCurrencyCode()));
        setDescription(String.format("%s vs %s", firstCurrency.getCurrencyName(), secondCurrency.getCurrencyName()));
        setFirstCurrency(firstCurrency);
        setSecondCurrency(secondCurrency);
    }
}
