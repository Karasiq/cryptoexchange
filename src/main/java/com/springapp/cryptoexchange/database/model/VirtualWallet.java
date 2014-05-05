package com.springapp.cryptoexchange.database.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

@Data
@Entity
@NoArgsConstructor
@RequiredArgsConstructor
@Table(name = "balances")
@ToString(exclude = "account", callSuper = false)
@EqualsAndHashCode(exclude = "virtualBalanceRef")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class VirtualWallet implements Serializable {
    @Id
    @GeneratedValue
    @JsonIgnore
    long id;

    @NonNull
    @ManyToOne(fetch = FetchType.EAGER)
    Currency currency;

    @JsonIgnore
    @Transient
    private final AtomicReference<BigDecimal> virtualBalanceRef = new AtomicReference<>(BigDecimal.ZERO);

    @Access(AccessType.PROPERTY)
    @Column(name = "virtual_balance", precision = 38, scale = 8, nullable = false)
    public BigDecimal getVirtualBalance() {
        return virtualBalanceRef.get();
    }

    public void setVirtualBalance(BigDecimal virtualBalance) {
        virtualBalanceRef.set(virtualBalance == null ? BigDecimal.ZERO : virtualBalance);
    }

    @Column(name = "external_balance", precision = 38, scale = 8)
    BigDecimal externalBalance = BigDecimal.ZERO;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    Account account;

    public void addBalance(@NonNull BigDecimal amount) {
        BigDecimal oldVal = virtualBalanceRef.get();
        virtualBalanceRef.compareAndSet(oldVal, oldVal.add(amount));
    }

    @PostLoad
    void init() {
        if(externalBalance == null) {
            setExternalBalance(BigDecimal.ZERO);
        }
    }
}
