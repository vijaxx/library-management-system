package com.vijaxx.library.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The overdue-fine rule, kept in one place so the return workflow, the overdue
 * report and the tests all agree.
 *
 * <p>Rule: {@code fine = max(0, days_late) * ratePerDay}, where {@code days_late}
 * is whole calendar days between the due date and the return date (or today, for
 * a loan still outstanding). Returning <em>on</em> the due date is not late, so a
 * same-day or early return always costs zero. Money is rounded half-up to two
 * decimals.
 */
public final class FinePolicy {

    /** Default rate used by the application: 5.00 per day late. */
    public static final BigDecimal DEFAULT_RATE_PER_DAY = new BigDecimal("5.00");

    private final BigDecimal ratePerDay;

    public FinePolicy(BigDecimal ratePerDay) {
        if (ratePerDay == null || ratePerDay.signum() < 0) {
            throw new IllegalArgumentException("Fine rate must be zero or positive");
        }
        this.ratePerDay = ratePerDay;
    }

    public static FinePolicy standard() {
        return new FinePolicy(DEFAULT_RATE_PER_DAY);
    }

    public BigDecimal ratePerDay() {
        return ratePerDay;
    }

    /** Whole days late; never negative. */
    public static long daysLate(LocalDate dueDate, LocalDate effectiveReturnDate) {
        return Math.max(0L, ChronoUnit.DAYS.between(dueDate, effectiveReturnDate));
    }

    /** Fine for a given number of days late. Negative input is treated as zero. */
    public BigDecimal fineForDaysLate(long daysLate) {
        if (daysLate <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return ratePerDay.multiply(BigDecimal.valueOf(daysLate)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Fine owed if a loan due on {@code dueDate} is handed back on {@code returnDate}. */
    public BigDecimal fineFor(LocalDate dueDate, LocalDate returnDate) {
        return fineForDaysLate(daysLate(dueDate, returnDate));
    }
}
