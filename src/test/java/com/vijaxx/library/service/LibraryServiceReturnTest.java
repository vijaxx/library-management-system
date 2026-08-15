package com.vijaxx.library.service;

import com.vijaxx.library.TestSupport;
import com.vijaxx.library.db.Database;
import com.vijaxx.library.model.Book;
import com.vijaxx.library.model.FinePolicy;
import com.vijaxx.library.model.Loan;
import com.vijaxx.library.model.Member;
import com.vijaxx.library.model.MembershipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The return half of the workflow: copy count restoration and fine calculation
 * at the service boundary, using a mutable clock to move "today" across the
 * due date without sleeping.
 */
class LibraryServiceReturnTest {

    private Database db;
    private MutableClock clock;
    private LibraryService service;
    private Book book;
    private Member member;

    /** A {@link Clock} whose instant can be moved forward mid-test. */
    static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void set(LocalDate date) { this.instant = date.atStartOfDay(ZoneOffset.UTC).toInstant(); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @BeforeEach
    void setUp() {
        db = TestSupport.freshDatabase();
        clock = new MutableClock(LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        service = new LibraryService(db, new FinePolicy(new BigDecimal("5.00")), clock);

        book = service.addBook(TestSupport.sampleBook("return-1", 1));
        member = service.addMember(TestSupport.sampleMember("returner@example.com", MembershipType.STANDARD));
    }

    @Test
    void returningOnTheDueDateChargesNoFine() {
        Loan loan = service.issueBook(book.getId(), member.getId());
        clock.set(loan.getDueDate()); // exactly on time

        ReturnReceipt receipt = service.returnBook(loan.getId());

        assertFalse(receipt.wasLate());
        assertEquals(new BigDecimal("0.00"), receipt.fine());
        assertEquals(1, service.findBook(book.getId()).orElseThrow().getAvailableCopies());
    }

    @Test
    void returningOneDayLateChargesOneDayOfFine() {
        Loan loan = service.issueBook(book.getId(), member.getId());
        clock.set(loan.getDueDate().plusDays(1));

        ReturnReceipt receipt = service.returnBook(loan.getId());

        assertTrue(receipt.wasLate());
        assertEquals(1, receipt.daysLate());
        assertEquals(new BigDecimal("5.00"), receipt.fine());
    }

    @Test
    void returningManyDaysLateChargesProportionally() {
        Loan loan = service.issueBook(book.getId(), member.getId());
        clock.set(loan.getDueDate().plusDays(14));

        ReturnReceipt receipt = service.returnBook(loan.getId());

        assertEquals(14, receipt.daysLate());
        assertEquals(new BigDecimal("70.00"), receipt.fine());
    }

    @Test
    void returningRestoresTheAvailableCopyCount() {
        Loan loan = service.issueBook(book.getId(), member.getId());
        assertEquals(0, service.findBook(book.getId()).orElseThrow().getAvailableCopies());

        service.returnBook(loan.getId());

        assertEquals(1, service.findBook(book.getId()).orElseThrow().getAvailableCopies());
    }

    @Test
    void returningTheSameLoanTwiceIsRejected() {
        Loan loan = service.issueBook(book.getId(), member.getId());
        service.returnBook(loan.getId());

        assertThrows(LibraryException.InvalidOperation.class, () -> service.returnBook(loan.getId()));
    }

    @Test
    void returningFreesTheBookUpForAnotherMember() {
        Loan loan = service.issueBook(book.getId(), member.getId());
        service.returnBook(loan.getId());

        Member other = service.addMember(TestSupport.sampleMember("other@example.com", MembershipType.STANDARD));
        Loan secondLoan = service.issueBook(book.getId(), other.getId()); // must not throw
        assertEquals(other.getId(), secondLoan.getMemberId());
    }

    @Test
    void returnByBookAndMemberFindsTheOpenLoan() {
        service.issueBook(book.getId(), member.getId());
        ReturnReceipt receipt = service.returnBook(book.getId(), member.getId());
        assertEquals(book.getId(), receipt.bookId());
        assertEquals(member.getId(), receipt.memberId());
    }
}
