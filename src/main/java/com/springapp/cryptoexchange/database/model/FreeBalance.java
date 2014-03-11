package com.springapp.cryptoexchange.database.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.math.BigDecimal;

@Table(name = "free_balance")
@Entity
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode(exclude = "amount")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class FreeBalance {
    public enum FeeType {
        TRADING, WITHDRAW
    }

    @Id
    @GeneratedValue
    long id;

    @OneToOne
    @NonNull Currency currency;

    @Column(name = "amount", precision = 38, scale = 8)
    BigDecimal amount = BigDecimal.ZERO;
}
