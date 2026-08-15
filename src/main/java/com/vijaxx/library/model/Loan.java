package com.vijaxx.library.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** One issue/return cycle of a single copy of a book to a member. */
public class Loan {

    private int id;
    private int bookId;
    private int memberId;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate returnDate;   // null while outstanding
    private BigDecimal fine = BigDecimal.ZERO;

    public Loan() {
    }

    public Loan(int id, int bookId, int memberId, LocalDate issueDate, LocalDate dueDate,
                LocalDate returnDate, BigDecimal fine) {
        this.id = id;
        this.bookId = bookId;
        this.memberId = memberId;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.fine = fine == null ? BigDecimal.ZERO : fine;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }

    public BigDecimal getFine() { return fine; }
    public void setFine(BigDecimal fine) { this.fine = fine == null ? BigDecimal.ZERO : fine; }

    public boolean isReturned() {
        return returnDate != null;
    }

    /**
     * Days late as of {@code asOf}. Zero when on time or already returned on time.
     * For a returned loan the comparison uses the actual return date.
     */
    public long daysOverdue(LocalDate asOf) {
        LocalDate reference = returnDate != null ? returnDate : asOf;
        long late = ChronoUnit.DAYS.between(dueDate, reference);
        return Math.max(0L, late);
    }

    @Override
    public String toString() {
        return "Loan#" + id + " book=" + bookId + " member=" + memberId
                + " due=" + dueDate + (returnDate == null ? " OPEN" : " returned=" + returnDate);
    }
}
