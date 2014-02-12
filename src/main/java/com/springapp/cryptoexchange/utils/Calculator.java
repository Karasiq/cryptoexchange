package com.springapp.cryptoexchange.utils;

import com.springapp.cryptoexchange.database.model.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Just some math
public class Calculator {
    public static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100.0);

    public static BigDecimal fee(BigDecimal amount, BigDecimal feePercent) {
        return amount.divide(ONE_HUNDRED, 8, RoundingMode.FLOOR).multiply(feePercent);
    }
    public static BigDecimal withFee(BigDecimal amount, BigDecimal feePercent) {
        return amount.add(fee(amount, feePercent));
    }
    public static BigDecimal withoutFee(BigDecimal amount, BigDecimal feePercent) {
        return amount.subtract(fee(amount, feePercent));
    }

    public static BigDecimal buyTotal(final BigDecimal amount, final BigDecimal price) {
        return amount.multiply(price);
    }

    public static BigDecimal totalRequired(Order.Type orderType, BigDecimal amount, BigDecimal price) {
        return orderType == Order.Type.BUY ? buyTotal(amount, price) : amount;
    }
}
