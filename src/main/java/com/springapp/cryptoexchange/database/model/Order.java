package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.springapp.cryptoexchange.database.MarketManagerImpl;
import lombok.*;
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
@RequiredArgsConstructor
@Table(name = "orders")
@ToString(of={"id", "type", "status", "amount", "price", "completedAmount", "total"})
@EqualsAndHashCode(of = {"id", "openDate", "type", "amount", "price", "tradingPair"})
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Order implements Serializable {
    public enum Status {
        OPEN, COMPLETED, PARTIALLY_COMPLETED, CANCELLED, PARTIALLY_CANCELLED
    }

    public enum Type {
        BUY, SELL
    }

    @Id
    @GeneratedValue
    @Column(unique = true)
    long id;

    @Column(name = "open_time", nullable = false)
    Date openDate = new Date();

    @Column(name = "update_time")
    Date updateDate = new Date();

    @Column(name = "close_time")
    Date closeDate;

    @Column(name = "status")
    Status status = Status.OPEN;

    @Column(name = "type")
    @NonNull Type type;

    @Column(name = "amount", precision = 38, scale = 8)
    @NonNull BigDecimal amount;

    @Column(name = "completed_amount", precision = 38, scale = 8)
    BigDecimal completedAmount = BigDecimal.ZERO;

    @Column(name = "total_sum", precision = 38, scale = 8)
    BigDecimal total = BigDecimal.ZERO;

    @Column(name = "price", precision = 38, scale = 8)
    @NonNull BigDecimal price;

    @ManyToOne
    @NonNull TradingPair tradingPair;

    @ManyToOne
    @JsonIgnore
    @NonNull VirtualWallet sourceWallet;

    @ManyToOne
    @JsonIgnore
    @NonNull VirtualWallet destWallet;

    @ManyToOne
    @JsonIgnore
    @NonNull Account account;

    public synchronized BigDecimal getRemainingAmount() {
        return amount.subtract(completedAmount);
    }

    public synchronized void addCompletedAmount(final @NonNull BigDecimal amount) throws MarketManagerImpl.MarketError {
        completedAmount = completedAmount.add(amount);
        if(completedAmount.compareTo(getAmount()) > 0) {
            throw new MarketManagerImpl.MarketError("Critical internal error");
        }
    }

    public synchronized void addTotal(final @NonNull BigDecimal total) {
        this.total = this.total.add(total);
    }

    public void cancel() {
        if (status.equals(Status.OPEN)) {
            setStatus(Status.CANCELLED);
        } else if (status.equals(Status.PARTIALLY_COMPLETED)) {
            setStatus(Status.PARTIALLY_CANCELLED);
        } else throw new IllegalArgumentException("Order already cancelled");
    }

    public boolean isActual() {
        return status.equals(Status.OPEN) || status.equals(Status.PARTIALLY_COMPLETED);
    }
}
