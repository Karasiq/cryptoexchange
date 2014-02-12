package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "currencies")
@EqualsAndHashCode(of = {"currencyCode", "currencyName", "currencyType"})
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class Currency implements Serializable {
    public static enum CurrencyType {
        PURE_VIRTUAL, CRYPTO
    }
    @Id
    @GeneratedValue
    @Column(unique = true)
    private long id;

    @Column(name = "enabled", nullable = false)
    @JsonIgnore
    private boolean enabled = true;

    @Column(name = "code", nullable = false, unique = true)
    private @NonNull String currencyCode;

    @Column(name = "name", nullable = false, unique = true)
    private @NonNull String currencyName;

    @Column(name = "withdraw_fee", precision = 5, scale = 2)
    @JsonIgnore
    private BigDecimal withdrawFee = BigDecimal.ONE;

    @Column(name = "type")
    private CurrencyType currencyType = CurrencyType.CRYPTO;

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
