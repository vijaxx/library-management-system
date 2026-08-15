package com.vijaxx.library.service;

import com.vijaxx.library.TestSupport;
import com.vijaxx.library.dao.LoanDao;
import com.vijaxx.library.db.ConnectionFactory;
import com.vijaxx.library.db.Database;
import com.vijaxx.library.model.Book;
import com.vijaxx.library.model.FinePolicy;
import com.vijaxx.library.model.Member;
import com.vijaxx.library.model.MembershipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves that a failed {@code issueBook} call cannot half-apply: the copy
 * decrement and the loan insert either both land or neither does, even when
 * the failure happens between the two writes.
 */
class TransactionRollbackTest {

    private Database db;
    private LibraryService service;

    @BeforeEach
    void setUp() {
        db = TestSupport.freshDatabase();
        service = new LibraryService(db, FinePolicy.standard(),
                Clock.fixed(java.time.LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }

    @Test
    void aRejectedIssueLeavesNoLoanRowAndNoCopyChange() {
        Book book = service.addBook(TestSupport.sampleBook("rollback-1", 1));
        Member first = service.addMember(TestSupport.sampleMember("r1@example.com", MembershipType.STUDENT));
        Member second = service.addMember(TestSupport.sampleMember("r2@example.com", MembershipType.STUDENT));

        service.issueBook(book.getId(), first.getId());

        assertThrows(LibraryException.NoCopiesAvailable.class,
                () -> service.issueBook(book.getId(), second.getId()));

        // Exactly one loan exists (the successful one) — the rejected attempt left no row.
        assertEquals(1, service.openLoansOf(first.getId()).size());
        assertEquals(0, service.openLoansOf(second.getId()).size());
        assertEquals(0, service.findBook(book.getId()).orElseThrow().getAvailableCopies());
    }

    @Test
    void aFailureAfterTheCopyDecrementRollsBackTheDecrementToo() throws Exception {
        // Wrap the real connection so the loan INSERT fails after the copy
        // decrement has already happened on the same (uncommitted) transaction.
        // If the service's commit/rollback handling is correct, the whole
        // transaction rolls back and available_copies is restored.
        Book book = service.addBook(TestSupport.sampleBook("rollback-2", 1));
        Member member = service.addMember(TestSupport.sampleMember("r3@example.com", MembershipType.STUDENT));

        ConnectionFactory failingAfterFirstWrite = () -> {
            Connection real = db.open();
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName()) && args.length > 0
                                && args[0] instanceof String sql && sql.startsWith("INSERT INTO loans")) {
                            throw new SQLException("simulated failure during loan insert");
                        }
                        return method.invoke(real, args);
                    });
        };

        LibraryService flaky = new LibraryService(failingAfterFirstWrite, FinePolicy.standard(),
                Clock.fixed(java.time.LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));

        assertThrows(LibraryException.DataAccess.class, () -> flaky.issueBook(book.getId(), member.getId()));

        // The copy decrement that happened earlier in the same transaction must
        // have been rolled back along with the failed insert.
        Book reloaded = service.findBook(book.getId()).orElseThrow();
        assertEquals(1, reloaded.getAvailableCopies(), "available_copies must be restored after rollback");
        try (Connection c = db.open()) {
            assertEquals(0, new LoanDao().countByBook(c, book.getId()), "no loan row should exist");
        }
    }
}
