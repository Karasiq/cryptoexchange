package com.springapp.cryptoexchange.database.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "addresses")
@EqualsAndHashCode(of = "address")
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Immutable
public class Address implements Serializable {
    @Id
    @GeneratedValue
    long id;

    @Size(min = 1)
    @NonNull
    @Column(name = "address", nullable = false, unique = true)
    String address;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    VirtualWallet virtualWallet;

    @Column(name = "generated_at", updatable = false)
    Date generateTime = new Date();
}
