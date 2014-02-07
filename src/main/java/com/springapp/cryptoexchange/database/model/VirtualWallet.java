package com.springapp.cryptoexchange.database.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
@RequiredArgsConstructor
@Table(name = "balances")
@ToString(exclude = "account", callSuper = false)
public class VirtualWallet implements Serializable {
    @Id
    @GeneratedValue
    @Column(unique = true)
    @JsonIgnore
    private long id;

    @NonNull
    @ManyToOne
    private Currency currency;

    @Column(name = "virtual_balance", precision = 38, scale = 8)
    private volatile BigDecimal virtualBalance = BigDecimal.ZERO;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private Account account;

    public synchronized void addBalance(final BigDecimal amount) {
        setVirtualBalance(virtualBalance.add(amount));
    }
}
