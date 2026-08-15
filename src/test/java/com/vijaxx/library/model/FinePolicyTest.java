package com.vijaxx.library.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fine calculation across the boundaries the brief calls out explicitly. */
class FinePolicyTest {

    private final FinePolicy policy = new FinePolicy(new BigDecimal("5.00"));

    @Test
    void returningOnTheDueDateIsNotLate() {
        LocalDate due = LocalDate.of(2026, 1, 10);
        assertEquals(new BigDecimal("0.00"), policy.fineFor(due, due));
    }

    @Test
    void returningBeforeTheDueDateIsNotLate() {
        LocalDate due = LocalDate.of(2026, 1, 10);
        LocalDate returned = due.minusDays(3);
        assertEquals(new BigDecimal("0.00"), policy.fineFor(due, returned));
    }

    @Test
    void oneDayLateChargesExactlyOneDayOfFine() {
        LocalDate due = LocalDate.of(2026, 1, 10);
        LocalDate returned = due.plusDays(1);
        assertEquals(new BigDecimal("5.00"), policy.fineFor(due, returned));
    }

    @Test
    void manyDaysLateScalesLinearly() {
        LocalDate due = LocalDate.of(2026, 1, 1);
        LocalDate returned = due.plusDays(30);
        assertEquals(new BigDecimal("150.00"), policy.fineFor(due, returned));
    }

    @Test
    void daysLateNeverGoesNegative() {
        LocalDate due = LocalDate.of(2026, 5, 1);
        LocalDate returned = due.minusDays(100);
        assertEquals(0L, FinePolicy.daysLate(due, returned));
    }

    @Test
    void zeroDaysLateProducesZeroFineRegardlessOfRate() {
        FinePolicy expensivePolicy = new FinePolicy(new BigDecimal("99.99"));
        assertEquals(new BigDecimal("0.00"), expensivePolicy.fineForDaysLate(0));
    }

    @Test
    void negativeRateIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new FinePolicy(new BigDecimal("-1.00")));
    }
}
