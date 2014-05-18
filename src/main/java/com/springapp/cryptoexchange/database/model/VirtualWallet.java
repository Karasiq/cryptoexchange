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
@Table(name = "balances", indexes = {
        @Index(columnList = "account_id, currency_id", unique = true)
})
@ToString(exclude = "account", callSuper = false)
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

    @Column(name = "virtual_balance", precision = 38, scale = 8, nullable = false)
    BigDecimal virtualBalance = BigDecimal.ZERO;

    @Column(name = "external_balance", precision = 38, scale = 8, nullable = false)
    BigDecimal externalBalance = BigDecimal.ZERO;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    Account account;

    public void addBalance(@NonNull BigDecimal amount) {
        setVirtualBalance(getVirtualBalance().add(amount));
    }
}
