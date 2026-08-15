package com.vijaxx.library.dao;

import com.vijaxx.library.model.Loan;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Loan persistence. Prepared statements only. */
public class LoanDao {

    private static final String COLUMNS =
            "id, book_id, member_id, issue_date, due_date, return_date, fine";

    public int insert(Connection c, Loan loan) throws SQLException {
        String sql = "INSERT INTO loans (book_id, member_id, issue_date, due_date, return_date, fine) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, loan.getBookId());
            ps.setInt(2, loan.getMemberId());
            ps.setDate(3, Date.valueOf(loan.getIssueDate()));
            ps.setDate(4, Date.valueOf(loan.getDueDate()));
            if (loan.getReturnDate() == null) {
                ps.setNull(5, java.sql.Types.DATE);
            } else {
                ps.setDate(5, Date.valueOf(loan.getReturnDate()));
            }
            ps.setBigDecimal(6, loan.getFine());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    loan.setId(keys.getInt(1));
                }
            }
        }
        return loan.getId();
    }

    public Optional<Loan> findById(Connection c, int loanId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM loans WHERE id = ?")) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Loan> findAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM loans ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            return mapAll(rs);
        }
    }

    /** All loans that have not been returned yet. */
    public List<Loan> findOpen(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM loans WHERE return_date IS NULL ORDER BY due_date");
             ResultSet rs = ps.executeQuery()) {
            return mapAll(rs);
        }
    }

    public List<Loan> findOpenByMember(Connection c, int memberId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS
                + " FROM loans WHERE member_id = ? AND return_date IS NULL ORDER BY due_date")) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        }
    }

    /** Outstanding-loan count for a member — the number checked against the tier limit. */
    public int countOpenByMember(Connection c, int memberId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE member_id = ? AND return_date IS NULL")) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** The oldest outstanding loan of a given book by a given member, if any. */
    public Optional<Loan> findOpenByBookAndMember(Connection c, int bookId, int memberId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS
                + " FROM loans WHERE book_id = ? AND member_id = ? AND return_date IS NULL "
                + "ORDER BY issue_date, id LIMIT 1")) {
            ps.setInt(1, bookId);
            ps.setInt(2, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public int countOpenByBook(Connection c, int bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE book_id = ? AND return_date IS NULL")) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countByBook(Connection c, int bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE book_id = ?")) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countByMember(Connection c, int memberId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM loans WHERE member_id = ?")) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Closes a loan. The {@code return_date IS NULL} guard makes the update
     * idempotent-safe: a second concurrent return affects no rows.
     *
     * @return true when this call was the one that closed the loan
     */
    public boolean markReturned(Connection c, int loanId, java.time.LocalDate returnDate,
                                BigDecimal fine) throws SQLException {
        String sql = "UPDATE loans SET return_date = ?, fine = ? "
                + "WHERE id = ? AND return_date IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(returnDate));
            ps.setBigDecimal(2, fine);
            ps.setInt(3, loanId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean delete(Connection c, int loanId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM loans WHERE id = ?")) {
            ps.setInt(1, loanId);
            return ps.executeUpdate() == 1;
        }
    }

    private static List<Loan> mapAll(ResultSet rs) throws SQLException {
        List<Loan> loans = new ArrayList<>();
        while (rs.next()) {
            loans.add(map(rs));
        }
        return loans;
    }

    private static Loan map(ResultSet rs) throws SQLException {
        Date returned = rs.getDate("return_date");
        return new Loan(
                rs.getInt("id"),
                rs.getInt("book_id"),
                rs.getInt("member_id"),
                rs.getDate("issue_date").toLocalDate(),
                rs.getDate("due_date").toLocalDate(),
                returned == null ? null : returned.toLocalDate(),
                rs.getBigDecimal("fine"));
    }
}
