package com.springapp.cryptoexchange.database.model;

import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "addresses")
@EqualsAndHashCode(exclude = "virtualWallet")
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class Address implements Serializable {
    @Id
    @GeneratedValue
    @Column(unique = true)
    private long id;

    @NonNull
    @Column(nullable = false, name = "address")
    private String address;

    @NonNull
    @ManyToOne
    private VirtualWallet virtualWallet;
}
