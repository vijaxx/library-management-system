package com.vijaxx.library.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What a completed return produced — used by the UI to show a confirmation. */
public record ReturnReceipt(int loanId,
                            int bookId,
                            String bookTitle,
                            int memberId,
                            String memberName,
                            LocalDate dueDate,
                            LocalDate returnDate,
                            long daysLate,
                            BigDecimal fine) {

    public boolean wasLate() {
        return daysLate > 0;
    }
}
