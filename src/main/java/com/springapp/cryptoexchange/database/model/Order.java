package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.springapp.cryptoexchange.database.MarketManager;
import lombok.*;

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
@EqualsAndHashCode(of={"id", "type", "status", "amount", "price", "completedAmount", "total"})
public class Order implements Serializable {
    public enum Status {
        OPEN, COMPLETED, PARTIALLY_COMPLETED, CANCELLED
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

    @Column(name = "amount")
    @NonNull BigDecimal amount;

    @Column(name = "completed_amount")
    @NonNull BigDecimal completedAmount = BigDecimal.ZERO;

    @Column(name = "total_sum")
    @NonNull BigDecimal total = BigDecimal.ZERO;

    @Column(name = "price")
    @NonNull BigDecimal price;

    @ManyToOne
    @JsonIgnore
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

    public synchronized void addCompletedAmount(final @NonNull BigDecimal amount) throws MarketManager.MarketError {
        completedAmount = completedAmount.add(amount);
        if(completedAmount.compareTo(getAmount()) > 0) {
            throw new MarketManager.MarketError("Critical internal error");
        }
    }

    public synchronized void addTotal(final @NonNull BigDecimal total) {
        this.total = this.total.add(total);
    }
}
