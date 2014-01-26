package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@NoArgsConstructor
@Table(name = "trading_pairs")
public class TradingPair implements Serializable {
    @Id
    @GeneratedValue
    @Column
    long id;

    @Column(name = "enabled")
    @JsonIgnore
    boolean enabled = true;

    @ManyToOne
    @JsonIgnore
    Currency firstCurrency;

    @ManyToOne
    @JsonIgnore
    Currency secondCurrency;

    // Basic:
    @Column(name = "name")
    String name;

    @Column(name = "description")
    String description;


    // Transient:
    @Column(name = "last_reset")
    @JsonIgnore
    Date lastReset = new Date();

    @Column(name = "volume")
    BigDecimal volume = BigDecimal.ZERO;

    @Column(name = "last_price")
    BigDecimal lastPrice = BigDecimal.ZERO;

    @Column(name = "high_price")
    BigDecimal dayHigh = BigDecimal.ZERO;

    @Column(name = "low_price")
    BigDecimal dayLow = BigDecimal.ZERO;

    // Settings:
    @Column(name = "min_trade_amount", nullable = false)
    BigDecimal minimalTradeAmount = BigDecimal.ZERO;

    @Column(name = "trading_fee", nullable = false)
    BigDecimal tradingFee = BigDecimal.valueOf(0.2);

    public TradingPair(Currency firstCurrency, Currency secondCurrency) {
        setName(String.format("%s/%s", firstCurrency.getCurrencyCode(), secondCurrency.getCurrencyCode()));
        setDescription(String.format("%s vs %s", firstCurrency.getCurrencyName(), secondCurrency.getCurrencyName()));
        setFirstCurrency(firstCurrency);
        setSecondCurrency(secondCurrency);
    }
}
