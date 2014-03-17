package com.springapp.cryptoexchange.database.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "addresses")
@EqualsAndHashCode(exclude = "virtualWallet")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Address implements Serializable {
    @Id
    @GeneratedValue
    long id;

    @NonNull
    @Column(nullable = false, name = "address")
    String address;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    VirtualWallet virtualWallet;

    @Column(name = "received_by_address")
    BigDecimal receivedByAddress = BigDecimal.ZERO;

    @PostLoad
    void init() {
        if(receivedByAddress == null) {
            receivedByAddress = BigDecimal.ZERO;
        }
    }
}
