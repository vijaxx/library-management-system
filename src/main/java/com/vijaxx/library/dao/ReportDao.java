package com.vijaxx.library.dao;

import com.vijaxx.library.model.FinePolicy;
import com.vijaxx.library.model.MembershipType;
import com.vijaxx.library.model.Reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reporting queries: multi-table joins with aggregation, all parameterised.
 *
 * <p>Day counts are computed in SQL ({@code DATEDIFF}); the money is applied in
 * Java through {@link FinePolicy} so the reports and the return workflow cannot
 * drift apart.
 */
public class ReportDao {

    private static final String LOAN_JOIN_COLUMNS =
            "l.id AS loan_id, l.book_id, b.isbn, b.title, b.author, "
          + "l.member_id, m.name AS member_name, m.membership_type, "
          + "l.issue_date, l.due_date, l.return_date, l.fine";

    private final FinePolicy finePolicy;

    public ReportDao(FinePolicy finePolicy) {
        this.finePolicy = finePolicy;
    }

    /** Every copy currently in a member's hands, newest due date last. */
    public List<Reports.LoanRow> currentlyBorrowed(Connection c, LocalDate asOf) throws SQLException {
        String sql = "SELECT " + LOAN_JOIN_COLUMNS + ", "
                + "DATEDIFF('DAY', l.due_date, CAST(? AS DATE)) AS days_late "
                + "FROM loans l "
                + "JOIN books b   ON b.id = l.book_id "
                + "JOIN members m ON m.id = l.member_id "
                + "WHERE l.return_date IS NULL "
                + "ORDER BY l.due_date, b.title";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(asOf));
            try (ResultSet rs = ps.executeQuery()) {
                return mapLoanRows(rs);
            }
        }
    }

    /** Outstanding loans whose due date has already passed, with the fine accrued so far. */
    public List<Reports.LoanRow> overdue(Connection c, LocalDate asOf) throws SQLException {
        String sql = "SELECT " + LOAN_JOIN_COLUMNS + ", "
                + "DATEDIFF('DAY', l.due_date, CAST(? AS DATE)) AS days_late "
                + "FROM loans l "
                + "JOIN books b   ON b.id = l.book_id "
                + "JOIN members m ON m.id = l.member_id "
                + "WHERE l.return_date IS NULL AND l.due_date < CAST(? AS DATE) "
                + "ORDER BY l.due_date, m.name";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(asOf));
            ps.setDate(2, Date.valueOf(asOf));
            try (ResultSet rs = ps.executeQuery()) {
                return mapLoanRows(rs);
            }
        }
    }

    /** Full loan history for one member. */
    public List<Reports.LoanRow> loanHistoryForMember(Connection c, int memberId, LocalDate asOf)
            throws SQLException {
        String sql = "SELECT " + LOAN_JOIN_COLUMNS + ", "
                + "DATEDIFF('DAY', l.due_date, COALESCE(l.return_date, CAST(? AS DATE))) AS days_late "
                + "FROM loans l "
                + "JOIN books b   ON b.id = l.book_id "
                + "JOIN members m ON m.id = l.member_id "
                + "WHERE l.member_id = ? "
                + "ORDER BY l.issue_date DESC, l.id DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(asOf));
            ps.setInt(2, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapLoanRows(rs);
            }
        }
    }

    /**
     * Titles ranked by how many times they have ever been issued. Books that
     * were never borrowed are included with a zero count, which is what makes
     * this a LEFT JOIN rather than an inner one.
     */
    public List<Reports.TitlePopularity> mostBorrowed(Connection c, int limit) throws SQLException {
        String sql = "SELECT b.id, b.title, b.author, b.category, "
                + "COUNT(l.id) AS times_borrowed, "
                + "COALESCE(SUM(CASE WHEN l.return_date IS NULL THEN 1 ELSE 0 END), 0) AS currently_out "
                + "FROM books b "
                + "LEFT JOIN loans l ON l.book_id = b.id "
                + "GROUP BY b.id, b.title, b.author, b.category "
                + "ORDER BY times_borrowed DESC, b.title ASC "
                + "LIMIT ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Reports.TitlePopularity> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new Reports.TitlePopularity(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("author"),
                            rs.getString("category"),
                            rs.getLong("times_borrowed"),
                            rs.getLong("currently_out")));
                }
                return rows;
            }
        }
    }

    /**
     * Per-member activity: lifetime loans, how many are still out, how many of
     * those are overdue as of {@code asOf}, and total fines already charged.
     */
    public List<Reports.MemberActivity> memberActivity(Connection c, LocalDate asOf) throws SQLException {
        String sql = "SELECT m.id, m.name, m.membership_type, "
                + "COUNT(l.id) AS total_loans, "
                + "COALESCE(SUM(CASE WHEN l.return_date IS NULL THEN 1 ELSE 0 END), 0) AS open_loans, "
                + "COALESCE(SUM(CASE WHEN l.return_date IS NULL AND l.due_date < CAST(? AS DATE) "
                + "                  THEN 1 ELSE 0 END), 0) AS overdue_loans, "
                + "COALESCE(SUM(l.fine), 0) AS fines_paid "
                + "FROM members m "
                + "LEFT JOIN loans l ON l.member_id = m.id "
                + "GROUP BY m.id, m.name, m.membership_type "
                + "ORDER BY total_loans DESC, m.name ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(asOf));
            try (ResultSet rs = ps.executeQuery()) {
                List<Reports.MemberActivity> rows = new ArrayList<>();
                while (rs.next()) {
                    BigDecimal fines = rs.getBigDecimal("fines_paid");
                    rows.add(new Reports.MemberActivity(
                            rs.getInt("id"),
                            rs.getString("name"),
                            MembershipType.valueOf(rs.getString("membership_type")),
                            rs.getLong("total_loans"),
                            rs.getLong("open_loans"),
                            rs.getLong("overdue_loans"),
                            (fines == null ? BigDecimal.ZERO : fines).setScale(2, RoundingMode.HALF_UP)));
                }
                return rows;
            }
        }
    }

    /** Total fines charged on returned loans across the whole library. */
    public BigDecimal totalFinesCollected(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(fine), 0) FROM loans WHERE return_date IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            BigDecimal total = rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            return (total == null ? BigDecimal.ZERO : total).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private List<Reports.LoanRow> mapLoanRows(ResultSet rs) throws SQLException {
        List<Reports.LoanRow> rows = new ArrayList<>();
        while (rs.next()) {
            Date returned = rs.getDate("return_date");
            long daysLate = Math.max(0L, rs.getLong("days_late"));
            BigDecimal fine = returned == null
                    ? finePolicy.fineForDaysLate(daysLate)          // accruing
                    : rs.getBigDecimal("fine");                     // already settled
            rows.add(new Reports.LoanRow(
                    rs.getInt("loan_id"),
                    rs.getInt("book_id"),
                    rs.getString("isbn"),
                    rs.getString("title"),
                    rs.getString("author"),
                    rs.getInt("member_id"),
                    rs.getString("member_name"),
                    MembershipType.valueOf(rs.getString("membership_type")),
                    rs.getDate("issue_date").toLocalDate(),
                    rs.getDate("due_date").toLocalDate(),
                    returned == null ? null : returned.toLocalDate(),
                    daysLate,
                    fine == null ? BigDecimal.ZERO : fine));
        }
        return rows;
    }
}
