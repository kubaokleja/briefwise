package com.briefwise.shared.domain.model;

import java.math.BigDecimal;
import java.util.Currency;

/** Amount in a currency. Arithmetic is only defined between amounts of the same currency. */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("Money needs an amount and a currency");
        }
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }
}
