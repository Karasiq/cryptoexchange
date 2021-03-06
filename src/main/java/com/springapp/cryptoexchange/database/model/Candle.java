package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.util.Assert;

import javax.persistence.*;
import javax.validation.constraints.DecimalMin;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@ToString(exclude = "tradingPair")
@NoArgsConstructor
@RequiredArgsConstructor
@Table(name = "history", indexes = {
        @Index(columnList = "tradingPair_id, open_time", unique = true)
})
@EqualsAndHashCode(of = {"openTime", "tradingPair"})
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Candle implements Serializable {
    @Id @GeneratedValue @JsonIgnore
    long id;

    @Column(name = "open_time", nullable = false, updatable = false)
    Date openTime = new Date();

    @Column(name = "close_time")
    Date closeTime;

    @Version
    @Column(name = "update_time")
    Date updateTime;

    @DecimalMin("0.00000001")
    @Column(name = "open", precision = 38, scale = 8)
    BigDecimal open;

    @DecimalMin("0.00000001")
    @Column(name = "high", precision = 38, scale = 8)
    BigDecimal high;

    @DecimalMin("0.00000001")
    @Column(name = "low", precision = 38, scale = 8)
    BigDecimal low;

    @DecimalMin("0.00000001")
    @Column(name = "close", precision = 38, scale = 8)
    BigDecimal close;

    @DecimalMin("0")
    @Column(name = "volume", precision = 38, scale = 2)
    BigDecimal volume = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY) @JsonIgnore @NonNull
    TradingPair tradingPair;

    public Candle(final TradingPair tradingPair, @NonNull final Candle previousCandle) { // open new candle
        this(tradingPair);
        Assert.isTrue(previousCandle.isClosed(), "Candle is still open");
        update(previousCandle.getClose(), BigDecimal.ZERO);
    }

    public void update(final @NonNull BigDecimal lastPrice, final @NonNull BigDecimal amount) {
        if(getHigh() == null || lastPrice.compareTo(getHigh()) > 0) {
            setHigh(lastPrice);
        }
        if(getLow() == null || lastPrice.compareTo(getLow()) < 0) {
            setLow(lastPrice);
        }
        if(getOpen() == null) {
            setOpen(lastPrice);
        }
        BigDecimal volume = getVolume();
        if(volume == null) {
            volume = amount;
        } else {
            volume = volume.add(amount);
        }
        setVolume(volume);
        setClose(lastPrice);
        setUpdateTime(new Date());
    }

    public boolean timeToClose(final @NonNull Period chartPeriod) {
        return getOpenTime() != null && DateTime.now().minus(chartPeriod).isAfter(new DateTime(getOpenTime()));
    }

    public boolean isClosed() {
        return getCloseTime() != null;
    }

    public void close() {
        setCloseTime(new Date());
    }
}
