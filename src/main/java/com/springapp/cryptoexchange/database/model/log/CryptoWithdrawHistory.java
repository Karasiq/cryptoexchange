package com.springapp.cryptoexchange.database.model.log;

import com.bitcoin.daemon.Address;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Table(name = "crypto_withdraw_history")
@Data
@Entity
@Immutable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CryptoWithdrawHistory {
    @Id
    @GeneratedValue
    long id;

    @ManyToOne
    VirtualWallet sourceWallet;

    @Column(name = "time")
    Date time = new Date();

    @Column(name = "amount", precision = 38, scale = 8)
    BigDecimal amount;

    @Column(name = "dest_address")
    String receiverAddress;

    @Column(name = "transaction")
    @Lob
    Address.Transaction transaction;

    public CryptoWithdrawHistory(@NonNull VirtualWallet sourceWallet, @NonNull Address.Transaction transaction) {
        setSourceWallet(sourceWallet);
        setAmount(transaction.getAmount().negate());
        setReceiverAddress(transaction.getAddress());
        setTransaction(transaction);
    }
}
