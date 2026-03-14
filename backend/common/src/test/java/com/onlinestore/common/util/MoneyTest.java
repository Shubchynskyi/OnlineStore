package com.onlinestore.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addShouldKeepCurrencyAndRoundAmount() {
        var first = Money.of(new BigDecimal("10.10"), "EUR");
        var second = Money.eur(new BigDecimal("5.905"));

        var result = first.add(second);

        assertThat(result.amount()).isEqualByComparingTo("16.01");
        assertThat(result.currency()).isEqualTo("EUR");
    }

    @Test
    void multiplyShouldScaleAmount() {
        var money = Money.of(new BigDecimal("12.345"), "EUR");

        var result = money.multiply(3);

        assertThat(result.amount()).isEqualByComparingTo("37.05");
        assertThat(result.currency()).isEqualTo("EUR");
    }

    @Test
    void addShouldRejectDifferentCurrencies() {
        var eur = Money.eur(new BigDecimal("1.00"));
        var usd = Money.of(new BigDecimal("1.00"), "USD");

        assertThatThrownBy(() -> eur.add(usd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("different currencies");
    }
}
