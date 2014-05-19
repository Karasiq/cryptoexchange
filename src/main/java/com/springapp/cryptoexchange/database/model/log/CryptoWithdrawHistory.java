package com.springapp.cryptoexchange.database.model.log;

import com.bitcoin.daemon.AbstractTransaction;
import com.bitcoin.daemon.Address;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.springapp.cryptoexchange.database.model.Currency;
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

    private Address.Transaction btcTransaction() {
        Address.Transaction transaction = new Address.Transaction();
        if(getTime() != null) transaction.setTime(getTime());
        transaction.setAddress(getReceiverAddress());
        transaction.setAmount(getAmount());
        transaction.setTxid(getTransactionId());
        transaction.setCategory("send");
        return transaction;
    }

    private AbstractTransaction abstractTransaction() {
        return new AbstractTransaction() {
            @Override
            public Object getAddress() {
                return getReceiverAddress();
            }

            @Override
            public String getTxid() {
                return CryptoWithdrawHistory.this.getTransactionId();
            }

            @Override
            public Date getTime() {
                return CryptoWithdrawHistory.this.getTime();
            }

            @Override
            public BigDecimal getFee() {
                return BigDecimal.ZERO;
            }

            @Override
            public BigDecimal getAmount() {
                return CryptoWithdrawHistory.this.getAmount();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractTransaction> T transaction(Currency.Type type) {
        switch (type) {
            case CRYPTO:
                return (T) btcTransaction();
            default:
                return (T) abstractTransaction();
        }
    }
}
