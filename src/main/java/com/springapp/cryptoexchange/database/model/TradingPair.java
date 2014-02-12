package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@NoArgsConstructor
@EqualsAndHashCode(of = {"firstCurrency", "secondCurrency"})
@Table(name = "trading_pairs")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class TradingPair implements Serializable {
    @Id
    @GeneratedValue
    @Column
    long id;

    @Column(name = "enabled")
    @JsonIgnore
    boolean enabled = true;

    @ManyToOne
    Currency firstCurrency;

    @ManyToOne
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

    @Column(name = "volume", precision = 38, scale = 2)
    BigDecimal volume = BigDecimal.ZERO;

    @Column(name = "last_price", precision = 38, scale = 8)
    BigDecimal lastPrice = BigDecimal.ZERO;

    @Column(name = "high_price", precision = 38, scale = 8)
    BigDecimal dayHigh = BigDecimal.ZERO;

    @Column(name = "low_price", precision = 38, scale = 8)
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
