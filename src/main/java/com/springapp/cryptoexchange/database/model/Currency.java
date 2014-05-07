package com.springapp.cryptoexchange.database.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "currencies")
@EqualsAndHashCode(of = {"code", "name", "type"})
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Currency implements Serializable {
    public static enum Type {
        PURE_VIRTUAL, CRYPTO
    }
    @Id
    @GeneratedValue
    long id;

    @Column(name = "enabled", nullable = false)
    boolean enabled = true;

    @Size(min = 1)
    @Column(name = "code", nullable = false, unique = true)
    @NonNull String code;

    @Size(min = 1)
    @Column(name = "name", nullable = false, unique = true)
    @NonNull String name;

    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "withdraw_fee", precision = 5, scale = 2)
    BigDecimal withdrawFee = BigDecimal.valueOf(0.2);

    @DecimalMin("0")
    @Column(name = "min_withdraw_amount", precision = 38, scale = 8)
    @NonNull BigDecimal minimalWithdrawAmount = BigDecimal.ZERO;

    @Column(name = "type")
    @NonNull
    Type type;

    @PostLoad
    void init() {
        if(withdrawFee == null) {
            withdrawFee = BigDecimal.ONE;
        }
        if(type == null) {
            type = Type.PURE_VIRTUAL;
        }
        if(minimalWithdrawAmount == null) {
            minimalWithdrawAmount = BigDecimal.ZERO;
        }
    }
}
