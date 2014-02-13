package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "currencies")
@EqualsAndHashCode(of = {"currencyCode", "currencyName", "currencyType"})
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Currency implements Serializable {
    public static enum CurrencyType {
        PURE_VIRTUAL, CRYPTO
    }
    @Id
    @GeneratedValue
    @Column(unique = true)
    long id;

    @Column(name = "enabled", nullable = false)
    @JsonIgnore
    boolean enabled = true;

    @Column(name = "code", nullable = false, unique = true)
    @NonNull String currencyCode;

    @Column(name = "name", nullable = false, unique = true)
    @NonNull String currencyName;

    @Column(name = "withdraw_fee", precision = 5, scale = 2)
    @JsonIgnore
    BigDecimal withdrawFee = BigDecimal.ONE;

    @Column(name = "type")
    @NonNull CurrencyType currencyType;

    @PostLoad
    void init() {
        if(withdrawFee == null) {
            withdrawFee = BigDecimal.ONE;
        }
        if(currencyType == null) {
            currencyType = CurrencyType.CRYPTO;
        }
    }
}
