package com.springapp.cryptoexchange.database.txlog;

import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import org.springframework.context.annotation.Profile;

import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.math.BigDecimal;

@Table(name = "internal_transactions_log")
@Data
@Entity
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@RequiredArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Immutable
@Profile("transaction-log")
public class InternalTransaction {
    @Id
    @GeneratedValue
    long id;

    @ManyToOne
    @NonNull
    VirtualWallet source;

    @ManyToOne
    @NonNull
    VirtualWallet dest;

    @Column(name = "amount", precision = 38, scale = 8, nullable = false)
    @NonNull
    BigDecimal amount;
}
