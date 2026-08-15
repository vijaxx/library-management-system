package com.vijaxx.library;

import com.vijaxx.library.db.Database;
import com.vijaxx.library.model.Book;
import com.vijaxx.library.model.Member;
import com.vijaxx.library.model.MembershipType;

/** Shared helpers for building a fresh, isolated database and sample rows per test. */
public final class TestSupport {

    private TestSupport() {
    }

    /** A brand-new in-memory H2 database with schema loaded but no seed data. */
    public static Database freshDatabase() {
        return Database.inMemory().initialize(false);
    }

    public static Book sampleBook(String isbn, int copies) {
        return Book.of(isbn, "Test Title " + isbn, "Test Author", "Testing", copies);
    }

    public static Member sampleMember(String email, MembershipType type) {
        return Member.of("Member " + email, email, "+91-90000-00000", type);
    }
}
