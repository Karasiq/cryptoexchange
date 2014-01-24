package com.springapp.cryptoexchange.database.model;


import com.bitcoin.daemon.CryptoCoinWallet;
import com.bitcoin.daemon.JsonRPC;
import lombok.*;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
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
    private long id;

    @NonNull
    @ManyToOne
    @JoinColumn(name = "currency")
    private Currency currency;

    @Column(name = "virtual_balance")
    private volatile BigDecimal virtualBalance = BigDecimal.ZERO;

    @OneToMany
    @JoinColumn(name = "addresses")
    private final Set<Address> addressList = new HashSet<>();

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY)
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
    public synchronized BigDecimal getBalance(@NonNull CryptoCoinWallet.Account wallet) {
        BigDecimal result = virtualBalance;
        if(!addressList.isEmpty()) synchronized (addressList) {
            Set<String> strings = new HashSet<>();
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
