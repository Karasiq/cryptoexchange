package com.springapp.cryptoexchange.database.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
@RequiredArgsConstructor
@Table(name = "balances")
@ToString(exclude = "account", callSuper = false)
@EqualsAndHashCode(exclude = "virtualBalance")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class VirtualWallet implements Serializable {
    @Id
    @GeneratedValue
    @Column(unique = true)
    @JsonIgnore
    long id;

    @NonNull
    @ManyToOne
    Currency currency;

    @Column(name = "virtual_balance", precision = 38, scale = 8)
    volatile BigDecimal virtualBalance = BigDecimal.ZERO;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    Account account;

    public synchronized void addBalance(final BigDecimal amount) {
        setVirtualBalance(virtualBalance.add(amount));
    }
}
