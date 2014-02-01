package com.springapp.cryptoexchange.database.model;


import com.bitcoin.daemon.AbstractWallet;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Entity
@NoArgsConstructor
@RequiredArgsConstructor
@Table(name = "balances")
@EqualsAndHashCode(of = {"id", "currency"})
@ToString(of = {"id", "currency", "virtualBalance"}, callSuper = false)
@Transactional
public class VirtualWallet implements Serializable {
    @Id
    @GeneratedValue
    @Column(unique = true)
    @JsonIgnore
    private long id;

    @NonNull
    @ManyToOne
    private Currency currency;

    @Column(name = "virtual_balance")
    private volatile BigDecimal virtualBalance = BigDecimal.ZERO;

    @OneToMany
    @JsonIgnore
    private final List<Address> addressList = new ArrayList<>();

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private Account account;

    @Transactional
    public Address addAddress(@NonNull String address) {
        Address newAddress = new Address(address, this);
        synchronized (addressList) {
            addressList.add(newAddress);
        }
        return newAddress;
    }

    @Transactional
    public synchronized BigDecimal getBalance(@NonNull AbstractWallet wallet) throws Exception {
        BigDecimal result = virtualBalance;
        if(!addressList.isEmpty()) synchronized (addressList) {
            Set<Object> strings = new HashSet<>();
            for(Address address : addressList) {
                strings.add(address.getAddress());
            }
            result = result.add(wallet.summaryConfirmedBalance(strings));
        }
        return result;
    }

    public synchronized void addBalance(final BigDecimal amount) {
        setVirtualBalance(virtualBalance.add(amount));
    }
}
