package com.vijaxx.library.service;

import com.vijaxx.library.TestSupport;
import com.vijaxx.library.db.Database;
import com.vijaxx.library.model.Book;
import com.vijaxx.library.model.FinePolicy;
import com.vijaxx.library.model.Loan;
import com.vijaxx.library.model.Member;
import com.vijaxx.library.model.MembershipType;
import com.vijaxx.library.model.Reports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Report queries: multi-table joins with aggregation. Verified against a small
 * hand-built dataset where the expected counts can be checked by inspection.
 */
class LibraryServiceReportsTest {

    private LibraryServiceReturnTest.MutableClock clock;
    private LibraryService service;

    @BeforeEach
    void setUp() {
        Database db = TestSupport.freshDatabase();
        clock = new LibraryServiceReturnTest.MutableClock(
                LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        service = new LibraryService(db, new FinePolicy(new BigDecimal("2.00")), clock);
    }

    @Test
    void currentlyBorrowedListsOnlyOpenLoans() {
        Book popular = service.addBook(TestSupport.sampleBook("rep-1", 2));
        Member a = service.addMember(TestSupport.sampleMember("repa@example.com", MembershipType.STANDARD));
        Member b = service.addMember(TestSupport.sampleMember("repb@example.com", MembershipType.STANDARD));

        Loan loanA = service.issueBook(popular.getId(), a.getId());
        service.issueBook(popular.getId(), b.getId());
        service.returnBook(loanA.getId()); // a returns theirs

        List<Reports.LoanRow> open = service.currentlyBorrowed();
        assertEquals(1, open.size());
        assertEquals(b.getId(), open.get(0).memberId());
    }

    @Test
    void overdueReportOnlyIncludesLoansPastTheirDueDate() {
        Book book = service.addBook(TestSupport.sampleBook("rep-2", 2));
        Member onTime = service.addMember(TestSupport.sampleMember("ontime@example.com", MembershipType.STANDARD));
        Member late = service.addMember(TestSupport.sampleMember("late@example.com", MembershipType.STANDARD));

        service.issueBook(book.getId(), onTime.getId());
        service.issueBook(book.getId(), late.getId());

        clock.set(LocalDate.of(2026, 1, 1).plusDays(MembershipType.STANDARD.loanPeriodDays() + 5));

        List<Reports.LoanRow> overdue = service.overdueLoans();
        assertEquals(2, overdue.size(), "both are now overdue since neither has returned");
        for (Reports.LoanRow row : overdue) {
            assertTrue(row.daysOverdue() > 0);
            assertEquals(new BigDecimal("10.00"), row.fine()); // 5 days late * 2.00/day
        }
    }

    @Test
    void mostBorrowedRanksByTimesIssuedDescending() {
        Book popular = service.addBook(TestSupport.sampleBook("rep-3", 3));
        Book rare = service.addBook(TestSupport.sampleBook("rep-4", 1));
        Member m1 = service.addMember(TestSupport.sampleMember("m1@example.com", MembershipType.PREMIUM));
        Member m2 = service.addMember(TestSupport.sampleMember("m2@example.com", MembershipType.PREMIUM));
        Member m3 = service.addMember(TestSupport.sampleMember("m3@example.com", MembershipType.PREMIUM));

        Loan l1 = service.issueBook(popular.getId(), m1.getId());
        service.returnBook(l1.getId());
        Loan l2 = service.issueBook(popular.getId(), m2.getId());
        service.returnBook(l2.getId());
        service.issueBook(popular.getId(), m3.getId());

        service.issueBook(rare.getId(), m1.getId()); // only one loan

        List<Reports.TitlePopularity> ranking = service.mostBorrowed(10);
        assertEquals(popular.getId(), ranking.get(0).bookId());
        assertEquals(3L, ranking.get(0).timesBorrowed());
        assertEquals(1L, ranking.get(0).currentlyOut());

        Reports.TitlePopularity rareRow = ranking.stream()
                .filter(r -> r.bookId() == rare.getId()).findFirst().orElseThrow();
        assertEquals(1L, rareRow.timesBorrowed());
    }

    @Test
    void mostBorrowedIncludesTitlesNeverIssuedWithZeroCount() {
        Book neverBorrowed = service.addBook(TestSupport.sampleBook("rep-5", 1));

        List<Reports.TitlePopularity> ranking = service.mostBorrowed(10);
        Reports.TitlePopularity row = ranking.stream()
                .filter(r -> r.bookId() == neverBorrowed.getId()).findFirst().orElseThrow();
        assertEquals(0L, row.timesBorrowed());
    }

    @Test
    void memberActivityAggregatesTotalOpenAndOverdueLoans() {
        Book book = service.addBook(TestSupport.sampleBook("rep-6", 2));
        Member member = service.addMember(TestSupport.sampleMember("active@example.com", MembershipType.STANDARD));

        Loan loan = service.issueBook(book.getId(), member.getId());
        clock.set(loan.getDueDate().plusDays(3));

        List<Reports.MemberActivity> activity = service.memberActivity();
        Reports.MemberActivity row = activity.stream()
                .filter(a -> a.memberId() == member.getId()).findFirst().orElseThrow();

        assertEquals(1, row.totalLoans());
        assertEquals(1, row.openLoans());
        assertEquals(1, row.overdueLoans());
    }

    @Test
    void totalFinesCollectedSumsOnlySettledFines() {
        Book book = service.addBook(TestSupport.sampleBook("rep-7", 1));
        Member member = service.addMember(TestSupport.sampleMember("fines@example.com", MembershipType.STANDARD));

        Loan loan = service.issueBook(book.getId(), member.getId());
        clock.set(loan.getDueDate().plusDays(4)); // 4 days late * 2.00 = 8.00
        service.returnBook(loan.getId());

        assertEquals(new BigDecimal("8.00"), service.totalFinesCollected());
    }
}
