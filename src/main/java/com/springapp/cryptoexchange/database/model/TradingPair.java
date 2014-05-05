package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

import javax.persistence.*;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Size;
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
    @Size(min = 1)
    @Column(name = "name")
    String name;

    @Size(min = 1)
    @Column(name = "description")
    String description;


    // Transient:
    @Column(name = "last_reset")
    @JsonIgnore
    Date lastReset = new Date();

    @DecimalMin("0")
    @Column(name = "volume", precision = 38, scale = 8)
    BigDecimal volume = BigDecimal.ZERO;

    @DecimalMin("0.00000001")
    @Column(name = "last_price", precision = 38, scale = 8)
    BigDecimal lastPrice = BigDecimal.ZERO;

    @DecimalMin("0.00000001")
    @Column(name = "high_price", precision = 38, scale = 8)
    BigDecimal dayHigh = BigDecimal.ZERO;

    @DecimalMin("0.00000001")
    @Column(name = "low_price", precision = 38, scale = 8)
    BigDecimal dayLow = BigDecimal.ZERO;

    // Settings:
    @DecimalMin("0")
    @Column(name = "min_trade_amount", nullable = false)
    BigDecimal minimalTradeAmount = BigDecimal.ZERO;

    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "trading_fee", nullable = false)
    BigDecimal tradingFee = BigDecimal.ZERO;

    public TradingPair(Currency firstCurrency, Currency secondCurrency) {
        setName(String.format("%s/%s", firstCurrency.getCode(), secondCurrency.getCode()));
        setDescription(String.format("%s vs %s", firstCurrency.getName(), secondCurrency.getName()));
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
