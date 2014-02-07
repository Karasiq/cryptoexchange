package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.joda.time.DateTime;
import org.joda.time.Period;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@ToString(exclude = "tradingPair")
@NoArgsConstructor
@RequiredArgsConstructor
@Table(name = "history")
public class Candle implements Serializable {
    @Id
    @GeneratedValue
    @Column
    @JsonIgnore
    long id;

    @Column(name = "open_time")
    Date openTime = new Date();

    @Column(name = "close_time")
    Date closeTime;

    @Column(name = "open", precision = 38, scale = 8)
    BigDecimal open;
    @Column(name = "high", precision = 38, scale = 8)
    BigDecimal high;
    @Column(name = "low", precision = 38, scale = 8)
    BigDecimal low;
    @Column(name = "close", precision = 38, scale = 8)
    BigDecimal close;
    @Column(name = "volume", precision = 38, scale = 2)
    BigDecimal volume;

    @ManyToOne
    @JsonIgnore
    @NonNull TradingPair tradingPair;

    public Candle(@NonNull final TradingPair tradingPair, @NonNull final BigDecimal lastPrice) { // open new candle
        this(tradingPair);
        setOpen(lastPrice);
        setClose(lastPrice);
        setHigh(lastPrice);
        setLow(lastPrice);
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
    }

    public boolean isClosed(final @NonNull Period chartPeriod) {
        return DateTime.now().minus(chartPeriod).isAfter(new DateTime(getOpenTime()));
    }

    public void close() {
        setCloseTime(new Date());
    }
}
