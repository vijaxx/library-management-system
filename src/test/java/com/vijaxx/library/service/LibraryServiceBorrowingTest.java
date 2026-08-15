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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core transactional workflow: issuing a book. Covers borrow-limit
 * enforcement, no-copies-available rejection, and that a rejected issue never
 * half-applies (no loan row, no copy decrement).
 */
class LibraryServiceBorrowingTest {

    private LibraryService service;
    private Database db;

    @BeforeEach
    void setUp() {
        db = TestSupport.freshDatabase();
        Clock fixedClock = Clock.fixed(LocalDate.of(2026, 6, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                ZoneOffset.UTC);
        service = new LibraryService(db, FinePolicy.standard(), fixedClock);
    }

    @Test
    void issuingABookDecrementsAvailableCopiesAndCreatesAnOpenLoan() {
        Book book = service.addBook(TestSupport.sampleBook("issue-1", 2));
        Member member = service.addMember(TestSupport.sampleMember("issue1@example.com", MembershipType.STANDARD));

        Loan loan = service.issueBook(book.getId(), member.getId());

        assertEquals(book.getId(), loan.getBookId());
        assertEquals(member.getId(), loan.getMemberId());
        assertTrue(loan.getId() > 0);
        assertEquals(LocalDate.of(2026, 6, 1), loan.getIssueDate());
        assertEquals(LocalDate.of(2026, 6, 1).plusDays(MembershipType.STANDARD.loanPeriodDays()), loan.getDueDate());

        Book reloaded = service.findBook(book.getId()).orElseThrow();
        assertEquals(1, reloaded.getAvailableCopies());
    }

    @Test
    void issuingTheLastCopyLeavesZeroAvailable() {
        Book book = service.addBook(TestSupport.sampleBook("issue-2", 1));
        Member member = service.addMember(TestSupport.sampleMember("issue2@example.com", MembershipType.STANDARD));

        service.issueBook(book.getId(), member.getId());

        Book reloaded = service.findBook(book.getId()).orElseThrow();
        assertEquals(0, reloaded.getAvailableCopies());
    }

    @Test
    void issuingWithNoCopiesAvailableIsRejectedAndNothingChanges() {
        Book book = service.addBook(TestSupport.sampleBook("issue-3", 1));
        Member first = service.addMember(TestSupport.sampleMember("first@example.com", MembershipType.STANDARD));
        Member second = service.addMember(TestSupport.sampleMember("second@example.com", MembershipType.STANDARD));

        service.issueBook(book.getId(), first.getId()); // takes the only copy

        assertThrows(LibraryException.NoCopiesAvailable.class,
                () -> service.issueBook(book.getId(), second.getId()));

        // The failed attempt must not have touched anything: still zero available,
        // and the second member holds no loans.
        Book reloaded = service.findBook(book.getId()).orElseThrow();
        assertEquals(0, reloaded.getAvailableCopies());
        assertEquals(0, service.openLoanCount(second.getId()));
    }

    @Test
    void borrowLimitIsEnforcedPerMembershipTier() {
        Member student = service.addMember(TestSupport.sampleMember("student@example.com", MembershipType.STUDENT));
        int limit = MembershipType.STUDENT.borrowLimit();

        for (int i = 0; i < limit; i++) {
            Book book = service.addBook(TestSupport.sampleBook("limit-" + i, 1));
            service.issueBook(book.getId(), student.getId());
        }

        Book oneMore = service.addBook(TestSupport.sampleBook("limit-extra", 1));
        LibraryException.BorrowLimitExceeded ex = assertThrows(LibraryException.BorrowLimitExceeded.class,
                () -> service.issueBook(oneMore.getId(), student.getId()));
        assertEquals(limit, ex.limit());
        assertEquals(limit, ex.currentlyHeld());

        // The rejected loan must not have decremented the extra book's copies.
        Book reloaded = service.findBook(oneMore.getId()).orElseThrow();
        assertEquals(1, reloaded.getAvailableCopies());
    }

    @Test
    void higherTierMembersGetAHigherBorrowLimit() {
        Member premium = service.addMember(TestSupport.sampleMember("premium@example.com", MembershipType.PREMIUM));
        for (int i = 0; i < MembershipType.STUDENT.borrowLimit() + 1; i++) {
            Book book = service.addBook(TestSupport.sampleBook("premium-" + i, 1));
            service.issueBook(book.getId(), premium.getId()); // must not throw for a PREMIUM member
        }
        assertEquals(MembershipType.STUDENT.borrowLimit() + 1, service.openLoanCount(premium.getId()));
    }

    @Test
    void aMemberCannotBorrowTwoCopiesOfTheSameTitleAtOnce() {
        Book book = service.addBook(TestSupport.sampleBook("dup-1", 2));
        Member member = service.addMember(TestSupport.sampleMember("dup@example.com", MembershipType.STANDARD));

        service.issueBook(book.getId(), member.getId());

        assertThrows(LibraryException.InvalidOperation.class,
                () -> service.issueBook(book.getId(), member.getId()));
    }

    @Test
    void inactiveMembersCannotBorrow() {
        Book book = service.addBook(TestSupport.sampleBook("inactive-1", 1));
        Member member = service.addMember(TestSupport.sampleMember("inactive@example.com", MembershipType.STANDARD));
        member.setActive(false);
        service.updateMember(member);

        assertThrows(LibraryException.InvalidOperation.class,
                () -> service.issueBook(book.getId(), member.getId()));
    }

    @Test
    void issuingForAMissingBookThrowsNotFound() {
        Member member = service.addMember(TestSupport.sampleMember("nf1@example.com", MembershipType.STANDARD));
        assertThrows(LibraryException.NotFound.class, () -> service.issueBook(999_999, member.getId()));
    }

    @Test
    void issuingForAMissingMemberThrowsNotFound() {
        Book book = service.addBook(TestSupport.sampleBook("nf2", 1));
        assertThrows(LibraryException.NotFound.class, () -> service.issueBook(book.getId(), 999_999));
    }
}
