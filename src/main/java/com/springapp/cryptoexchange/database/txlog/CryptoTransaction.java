package com.springapp.cryptoexchange.database.txlog;

import com.springapp.cryptoexchange.database.model.Address;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import org.springframework.context.annotation.Profile;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Table(name = "crypto_transactions_log")
@Entity
@NoArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Immutable
@Profile("transaction-log")
public class CryptoTransaction {
    @Id
    @Column(name = "txid", unique = true)
    String transactionId;

    @ManyToOne
    Address address;

    @Column(name = "category")
    String category;

    @Column(name = "amount")
    BigDecimal amount;

    @Column(name = "fee", precision = 38, scale = 8)
    BigDecimal fee;

    @Column(name = "time")
    Date time;



    public CryptoTransaction(@NonNull com.bitcoin.daemon.Address.Transaction transaction, @NonNull Address address) {
        setCategory(transaction.getCategory());
        setAddress(address);
        setAmount(transaction.getAmount());
        setFee(transaction.getFee());
        setTime(new Date(transaction.getTime()));
        setTransactionId(transaction.getTxid());
    }
}
