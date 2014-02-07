package com.springapp.cryptoexchange.database.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;

@Table(name = "free_balance")
@Entity
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class FreeBalance {
    @Id
    @GeneratedValue
    long id;

    @OneToOne
    @NonNull Currency currency;

    @Column(name = "fee", precision = 38, scale = 8)
    BigDecimal collectedFee = BigDecimal.ZERO;
}
