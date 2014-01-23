package com.springapp.cryptoexchange.database.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.persistence.*;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "addresses")
public class Address {
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
