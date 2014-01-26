package com.springapp.cryptoexchange;

import com.springapp.cryptoexchange.database.model.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Just some math
public class Calculator {
    public static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100.0);

    public static BigDecimal withFee(BigDecimal amount, BigDecimal feePercent) {
        return amount.multiply(ONE_HUNDRED.add(feePercent)).divide(ONE_HUNDRED, 8, RoundingMode.FLOOR);
    }
    public static BigDecimal withoutFee(BigDecimal amount, BigDecimal feePercent) {
        return amount.multiply(ONE_HUNDRED).divide(ONE_HUNDRED.add(feePercent), 8, RoundingMode.FLOOR);
    }

    public static BigDecimal buyTotal(final BigDecimal amount, final BigDecimal price) {
        return amount.multiply(price);
    }

    public static BigDecimal totalRequired(Order.Type orderType, BigDecimal amount, BigDecimal price, BigDecimal feePercent) {
        return orderType == Order.Type.BUY ? withFee(buyTotal(amount, price), feePercent) : amount;
    }
}
