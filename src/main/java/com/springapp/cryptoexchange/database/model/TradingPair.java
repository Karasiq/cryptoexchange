package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;

import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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
    long id;

    @Column(name = "enabled")
    boolean enabled = true;

    @ManyToOne(fetch = FetchType.EAGER)
    Currency firstCurrency;

    @ManyToOne(fetch = FetchType.EAGER)
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

    @Column(name = "volume", precision = 38, scale = 8)
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
    BigDecimal tradingFee = BigDecimal.ZERO;

    public TradingPair(Currency firstCurrency, Currency secondCurrency) {
        setName(String.format("%s/%s", firstCurrency.getCurrencyCode(), secondCurrency.getCurrencyCode()));
        setDescription(String.format("%s vs %s", firstCurrency.getCurrencyName(), secondCurrency.getCurrencyName()));
        setFirstCurrency(firstCurrency);
        setSecondCurrency(secondCurrency);
    }

    @Cascade(CascadeType.DELETE)
    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, mappedBy = "tradingPair")
    @OrderBy("openTime desc")
    @JsonIgnore
    List<Candle> history;

    @Cascade(CascadeType.DELETE)
    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, mappedBy = "tradingPair")
    @OrderBy("openDate desc")
    @JsonIgnore
    List<Order> orders;
}
