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
    long id;

    @Column(name = "open_time", nullable = false, updatable = false)
    Date openDate = new Date();

    @Column(name = "update_time")
    Date updateDate = new Date();

    @Column(name = "close_time")
    Date closeDate;

    @Column(name = "status", nullable = false)
    Status status = Status.OPEN;

    @Column(name = "type", nullable = false)
    @NonNull Type type;

    @Column(name = "amount", precision = 38, scale = 8, nullable = false)
    @NonNull BigDecimal amount;

    @Column(name = "completed_amount", precision = 38, scale = 8)
    BigDecimal completedAmount = BigDecimal.ZERO;

    @Column(name = "total_sum", precision = 38, scale = 8)
    BigDecimal total = BigDecimal.ZERO;

    @Column(name = "price", precision = 38, scale = 8, nullable = false)
    @NonNull BigDecimal price;

    @ManyToOne(fetch = FetchType.EAGER)
    @NonNull TradingPair tradingPair;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @NonNull VirtualWallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @NonNull VirtualWallet destWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @NonNull Account account;

    public BigDecimal getRemainingAmount() {
        return amount.subtract(completedAmount);
    }

    public void addCompletedAmount(final @NonNull BigDecimal amount) throws MarketManagerImpl.MarketError {
        setCompletedAmount(completedAmount.add(amount));
        if(completedAmount.compareTo(getAmount()) > 0) {
            throw new MarketManagerImpl.MarketError("Critical internal error");
        }
    }

    public void addTotal(final @NonNull BigDecimal total) {
        setTotal(this.total.add(total));
    }

    public void updateCompletionStatus() {
        final Date now = new Date();
        if(getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            setStatus(Order.Status.COMPLETED);
            setCloseDate(now);
        } else {
            setStatus(Order.Status.PARTIALLY_COMPLETED);
        }
        setUpdateDate(now);
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
