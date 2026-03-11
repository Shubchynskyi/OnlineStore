package com.onlinestore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.onlinestore.common.util.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void shouldAddMoneyInSameCurrency() {
        Money first = Money.of(new BigDecimal("10.10"), "EUR");
        Money second = Money.of(new BigDecimal("5.90"), "EUR");
        Money result = first.add(second);
        assertEquals(new BigDecimal("16.00"), result.amount());
        assertEquals("EUR", result.currency());
    }

    @Test
    void shouldRoundScaleToTwo() {
        Money value = Money.of(new BigDecimal("10.129"), "EUR");
        assertEquals(new BigDecimal("10.13"), value.amount());
    }

    @Test
    void shouldRejectDifferentCurrencies() {
        Money eur = Money.of(new BigDecimal("1.00"), "EUR");
        Money usd = Money.of(new BigDecimal("1.00"), "USD");
        assertThrows(IllegalArgumentException.class, () -> eur.add(usd));
    }
}
