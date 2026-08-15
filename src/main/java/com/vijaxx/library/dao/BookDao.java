package com.vijaxx.library.dao;

import com.vijaxx.library.model.Book;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Book persistence. Every statement is a {@link PreparedStatement} with bound
 * parameters — no SQL string is ever built by concatenating user input.
 *
 * <p>Each method takes the {@link Connection} to use so that the caller (the
 * service layer) owns the transaction boundary.
 */
public class BookDao {

    private static final String COLUMNS =
            "id, isbn, title, author, category, total_copies, available_copies";

    public int insert(Connection c, Book book) throws SQLException {
        String sql = "INSERT INTO books (isbn, title, author, category, total_copies, available_copies) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthor());
            ps.setString(4, book.getCategory());
            ps.setInt(5, book.getTotalCopies());
            ps.setInt(6, book.getAvailableCopies());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    book.setId(keys.getInt(1));
                }
            }
        }
        return book.getId();
    }

    public boolean update(Connection c, Book book) throws SQLException {
        String sql = "UPDATE books SET isbn = ?, title = ?, author = ?, category = ?, "
                + "total_copies = ?, available_copies = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, book.getIsbn());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getAuthor());
            ps.setString(4, book.getCategory());
            ps.setInt(5, book.getTotalCopies());
            ps.setInt(6, book.getAvailableCopies());
            ps.setInt(7, book.getId());
            return ps.executeUpdate() == 1;
        }
    }

    public boolean delete(Connection c, int bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM books WHERE id = ?")) {
            ps.setInt(1, bookId);
            return ps.executeUpdate() == 1;
        }
    }

    public Optional<Book> findById(Connection c, int bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM books WHERE id = ?")) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Book> findByIsbn(Connection c, String isbn) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM books WHERE isbn = ?")) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Book> findAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM books ORDER BY title");
             ResultSet rs = ps.executeQuery()) {
            return mapAll(rs);
        }
    }

    /**
     * Case-insensitive substring match across title, author, ISBN and category.
     * The search term is bound as a parameter, so quotes and SQL keywords in it
     * are inert data.
     */
    public List<Book> search(Connection c, String term) throws SQLException {
        String pattern = "%" + (term == null ? "" : term.toLowerCase()) + "%";
        String sql = "SELECT " + COLUMNS + " FROM books WHERE "
                + "LOWER(title) LIKE ? OR LOWER(author) LIKE ? "
                + "OR LOWER(isbn) LIKE ? OR LOWER(category) LIKE ? ORDER BY title";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) {
                ps.setString(i, pattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapAll(rs);
            }
        }
    }

    public List<String> findCategories(Connection c) throws SQLException {
        List<String> categories = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT category FROM books ORDER BY category");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString(1));
            }
        }
        return categories;
    }

    /**
     * Conditionally moves {@code available_copies} by {@code delta}. The WHERE
     * clause carries the invariant, so a concurrent issue cannot drive the count
     * negative or above the total: the update simply affects zero rows and the
     * caller rolls back.
     *
     * @return number of rows changed — 1 on success, 0 when the move was illegal
     */
    public int adjustAvailableCopies(Connection c, int bookId, int delta) throws SQLException {
        String sql = "UPDATE books SET available_copies = available_copies + ? "
                + "WHERE id = ? AND available_copies + ? >= 0 AND available_copies + ? <= total_copies";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, bookId);
            ps.setInt(3, delta);
            ps.setInt(4, delta);
            return ps.executeUpdate();
        }
    }

    public int count(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM books");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static List<Book> mapAll(ResultSet rs) throws SQLException {
        List<Book> books = new ArrayList<>();
        while (rs.next()) {
            books.add(map(rs));
        }
        return books;
    }

    private static Book map(ResultSet rs) throws SQLException {
        return new Book(
                rs.getInt("id"),
                rs.getString("isbn"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("category"),
                rs.getInt("total_copies"),
                rs.getInt("available_copies"));
    }
}
