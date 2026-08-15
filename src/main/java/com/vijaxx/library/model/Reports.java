package com.vijaxx.library.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only row shapes produced by the reporting queries. These are join
 * results, not entities, so they live apart from {@link Book}/{@link Member}/{@link Loan}.
 */
public final class Reports {

    private Reports() {
    }

    /** One outstanding or historical loan, flattened with the book and member it refers to. */
    public record LoanRow(int loanId,
                          int bookId,
                          String isbn,
                          String title,
                          String author,
                          int memberId,
                          String memberName,
                          MembershipType membershipType,
                          LocalDate issueDate,
                          LocalDate dueDate,
                          LocalDate returnDate,
                          long daysOverdue,
                          BigDecimal fine) {

        public boolean isOpen() {
            return returnDate == null;
        }
    }

    /** Aggregate: how often a title has been issued. */
    public record TitlePopularity(int bookId,
                                  String title,
                                  String author,
                                  String category,
                                  long timesBorrowed,
                                  long currentlyOut) {
    }

    /** Aggregate: per-member borrowing activity. */
    public record MemberActivity(int memberId,
                                 String memberName,
                                 MembershipType membershipType,
                                 long totalLoans,
                                 long openLoans,
                                 long overdueLoans,
                                 BigDecimal finesPaid) {
    }
}
