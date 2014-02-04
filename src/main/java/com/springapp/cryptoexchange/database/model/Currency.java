package com.springapp.cryptoexchange.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "currencies")
@EqualsAndHashCode(of = {"currencyCode", "currencyName"})
@ToString(of = {"id", "currencyCode", "currencyName"}, callSuper = false)
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

    @Column(name = "withdraw_fee", nullable = false)
    @JsonIgnore
    private BigDecimal withdrawFee = BigDecimal.valueOf(3);

    @Column(name = "collected_fee")
    @JsonIgnore
    private BigDecimal collectedFee = BigDecimal.ZERO;

    @Column(name = "type")
    private CurrencyType currencyType = CurrencyType.CRYPTO;
}
