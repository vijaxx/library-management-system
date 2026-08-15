package com.vijaxx.library.dao;

import com.vijaxx.library.TestSupport;
import com.vijaxx.library.db.Database;
import com.vijaxx.library.model.Member;
import com.vijaxx.library.model.MembershipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DAO-level CRUD for members. */
class MemberDaoTest {

    private final MemberDao dao = new MemberDao();
    private Database db;

    @BeforeEach
    void setUp() {
        db = TestSupport.freshDatabase();
    }

    @Test
    void insertThenFindByIdRoundTrips() throws Exception {
        try (Connection c = db.open()) {
            Member member = TestSupport.sampleMember("a@example.com", MembershipType.STUDENT);
            int id = dao.insert(c, member);

            Member reloaded = dao.findById(c, id).orElseThrow();
            assertEquals("a@example.com", reloaded.getEmail());
            assertEquals(MembershipType.STUDENT, reloaded.getMembershipType());
            assertTrue(reloaded.isActive());
        }
    }

    @Test
    void updateChangesMembershipType() throws Exception {
        try (Connection c = db.open()) {
            Member member = TestSupport.sampleMember("b@example.com", MembershipType.STUDENT);
            dao.insert(c, member);

            member.setMembershipType(MembershipType.PREMIUM);
            dao.update(c, member);

            Member reloaded = dao.findById(c, member.getId()).orElseThrow();
            assertEquals(MembershipType.PREMIUM, reloaded.getMembershipType());
        }
    }

    @Test
    void deleteRemovesTheMember() throws Exception {
        try (Connection c = db.open()) {
            Member member = TestSupport.sampleMember("c@example.com", MembershipType.STANDARD);
            dao.insert(c, member);
            assertTrue(dao.delete(c, member.getId()));
            assertTrue(dao.findById(c, member.getId()).isEmpty());
        }
    }

    @Test
    void findByEmailIsCaseInsensitive() throws Exception {
        try (Connection c = db.open()) {
            dao.insert(c, TestSupport.sampleMember("Mixed.Case@Example.com", MembershipType.STANDARD));
            assertTrue(dao.findByEmail(c, "mixed.case@example.com").isPresent());
        }
    }

    @Test
    void searchMatchesNameEmailAndPhone() throws Exception {
        try (Connection c = db.open()) {
            dao.insert(c, Member.of("Priya Nair", "priya.nair@example.com", "+91-99999-11111", MembershipType.PREMIUM));

            List<Member> byName = dao.search(c, "priya");
            List<Member> byEmail = dao.search(c, "nair@example");
            List<Member> byPhone = dao.search(c, "99999");

            assertEquals(1, byName.size());
            assertEquals(1, byEmail.size());
            assertEquals(1, byPhone.size());
        }
    }
}
