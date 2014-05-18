package com.springapp.cryptoexchange.database.model.log;

import com.bitcoin.daemon.Address;
import com.springapp.cryptoexchange.database.model.VirtualWallet;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Table(name = "crypto_withdraw_history", indexes = {
        @Index(columnList = "sourceWallet_id, time")
})
@Data
@Entity
@Immutable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@NoArgsConstructor
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CryptoWithdrawHistory {
    @Id
    @GeneratedValue
    long id;

    @Column(name = "time")
    Date time = new Date();

    @NonNull
    @ManyToOne
    VirtualWallet sourceWallet;

    @NonNull
    @Column(name = "dest_address")
    String receiverAddress;

    @NonNull
    @Column(name = "amount", precision = 38, scale = 8)
    BigDecimal amount;

    @NonNull
    @Column(name = "txid")
    String transactionId;

    public Address.Transaction btcTransaction() {
        Address.Transaction transaction = new Address.Transaction();
        if(getTime() != null) transaction.setTime(getTime().getTime());
        transaction.setAddress(getReceiverAddress());
        transaction.setAmount(getAmount());
        transaction.setTxid(getTransactionId());
        transaction.setCategory("send");
        return transaction;
    }
}
