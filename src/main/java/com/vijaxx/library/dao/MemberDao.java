package com.vijaxx.library.dao;

import com.vijaxx.library.model.Member;
import com.vijaxx.library.model.MembershipType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Member persistence. Prepared statements only. */
public class MemberDao {

    private static final String COLUMNS =
            "id, name, email, phone, membership_type, join_date, active";

    public int insert(Connection c, Member member) throws SQLException {
        String sql = "INSERT INTO members (name, email, phone, membership_type, join_date, active) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, member.getName());
            ps.setString(2, member.getEmail());
            ps.setString(3, member.getPhone());
            ps.setString(4, member.getMembershipType().name());
            ps.setDate(5, Date.valueOf(member.getJoinDate()));
            ps.setBoolean(6, member.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    member.setId(keys.getInt(1));
                }
            }
        }
        return member.getId();
    }

    public boolean update(Connection c, Member member) throws SQLException {
        String sql = "UPDATE members SET name = ?, email = ?, phone = ?, membership_type = ?, "
                + "join_date = ?, active = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, member.getName());
            ps.setString(2, member.getEmail());
            ps.setString(3, member.getPhone());
            ps.setString(4, member.getMembershipType().name());
            ps.setDate(5, Date.valueOf(member.getJoinDate()));
            ps.setBoolean(6, member.isActive());
            ps.setInt(7, member.getId());
            return ps.executeUpdate() == 1;
        }
    }

    public boolean delete(Connection c, int memberId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE id = ?")) {
            ps.setInt(1, memberId);
            return ps.executeUpdate() == 1;
        }
    }

    public Optional<Member> findById(Connection c, int memberId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM members WHERE id = ?")) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Member> findByEmail(Connection c, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM members WHERE LOWER(email) = LOWER(?)")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Member> findAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM members ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            return mapAll(rs);
        }
    }

    /**
     * Case-insensitive substring match across name, email and phone.
     *
     * <p>{@code term} is matched literally -- a search for {@code "_"} or
     * {@code "%"} looks for that literal character rather than acting as a
     * wildcard. See {@link LikePatterns}.
     */
    public List<Member> search(Connection c, String term) throws SQLException {
        String pattern = LikePatterns.substringMatch(term == null ? null : term.toLowerCase());
        String sql = "SELECT " + COLUMNS + " FROM members WHERE "
                + "LOWER(name) LIKE ? ESCAPE '\\' OR LOWER(email) LIKE ? ESCAPE '\\' "
                + "OR LOWER(COALESCE(phone, '')) LIKE ? ESCAPE '\\' "
                + "ORDER BY name";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= 3; i++) {
                ps.setString(i, pattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        }
    }

    public int count(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM members");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static List<Member> mapAll(ResultSet rs) throws SQLException {
        List<Member> members = new ArrayList<>();
        while (rs.next()) {
            members.add(map(rs));
        }
        return members;
    }

    private static Member map(ResultSet rs) throws SQLException {
        return new Member(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                MembershipType.valueOf(rs.getString("membership_type")),
                rs.getDate("join_date").toLocalDate(),
                rs.getBoolean("active"));
    }
}
