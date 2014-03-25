package com.springapp.cryptoexchange.database.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "addresses")
@EqualsAndHashCode(exclude = "virtualWallet")
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Immutable
public class Address implements Serializable {
    @Id
    @GeneratedValue
    long id;

    @NonNull
    @Column(name = "address", nullable = false)
    String address;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    VirtualWallet virtualWallet;

    @Column(name = "generated_at", updatable = false)
    Date generateTime = new Date();
}
