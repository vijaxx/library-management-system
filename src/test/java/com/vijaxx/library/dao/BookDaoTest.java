package com.vijaxx.library.dao;

import com.vijaxx.library.TestSupport;
import com.vijaxx.library.db.Database;
import com.vijaxx.library.model.Book;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DAO-level CRUD and the guarded copy-count update, exercised directly against H2. */
class BookDaoTest {

    private final BookDao dao = new BookDao();
    private Database db;

    @BeforeEach
    void setUp() {
        db = TestSupport.freshDatabase();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection c = db.open()) {
            c.createStatement().execute("DROP ALL OBJECTS");
        }
    }

    @Test
    void insertAssignsGeneratedIdAndPersists() throws Exception {
        try (Connection c = db.open()) {
            Book book = TestSupport.sampleBook("111", 3);
            int id = dao.insert(c, book);
            assertTrue(id > 0);

            Optional<Book> found = dao.findById(c, id);
            assertTrue(found.isPresent());
            assertEquals("111", found.get().getIsbn());
            assertEquals(3, found.get().getAvailableCopies());
        }
    }

    @Test
    void updateChangesPersistedFields() throws Exception {
        try (Connection c = db.open()) {
            Book book = TestSupport.sampleBook("222", 2);
            dao.insert(c, book);

            book.setTitle("Updated Title");
            book.setTotalCopies(5);
            book.setAvailableCopies(5);
            assertTrue(dao.update(c, book));

            Book reloaded = dao.findById(c, book.getId()).orElseThrow();
            assertEquals("Updated Title", reloaded.getTitle());
            assertEquals(5, reloaded.getTotalCopies());
        }
    }

    @Test
    void deleteRemovesTheRow() throws Exception {
        try (Connection c = db.open()) {
            Book book = TestSupport.sampleBook("333", 1);
            dao.insert(c, book);
            assertTrue(dao.delete(c, book.getId()));
            assertTrue(dao.findById(c, book.getId()).isEmpty());
        }
    }

    @Test
    void findByIsbnLocatesAnExistingBook() throws Exception {
        try (Connection c = db.open()) {
            Book book = TestSupport.sampleBook("444-unique", 1);
            dao.insert(c, book);
            assertTrue(dao.findByIsbn(c, "444-unique").isPresent());
            assertFalse(dao.findByIsbn(c, "does-not-exist").isPresent());
        }
    }

    @Test
    void adjustAvailableCopiesRefusesToGoBelowZero() throws Exception {
        try (Connection c = db.open()) {
            Book book = TestSupport.sampleBook("555", 1);
            dao.insert(c, book);
            dao.adjustAvailableCopies(c, book.getId(), -1); // now 0 available

            int rowsChanged = dao.adjustAvailableCopies(c, book.getId(), -1); // would go to -1
            assertEquals(0, rowsChanged, "guarded update must reject going negative");

            Book reloaded = dao.findById(c, book.getId()).orElseThrow();
            assertEquals(0, reloaded.getAvailableCopies());
        }
    }

    @Test
    void adjustAvailableCopiesRefusesToExceedTotal() throws Exception {
        try (Connection c = db.open()) {
            Book book = TestSupport.sampleBook("666", 2);
            dao.insert(c, book); // available == total == 2

            int rowsChanged = dao.adjustAvailableCopies(c, book.getId(), +1); // would go to 3 > total
            assertEquals(0, rowsChanged, "guarded update must reject exceeding total copies");
        }
    }

    @Test
    void searchMatchesTitleAuthorIsbnAndCategoryCaseInsensitively() throws Exception {
        try (Connection c = db.open()) {
            dao.insert(c, Book.of("777", "The Great Gatsby", "F. Scott Fitzgerald", "Fiction", 1));

            List<Book> byTitle = dao.search(c, "GREAT");
            List<Book> byAuthor = dao.search(c, "fitzgerald");
            List<Book> byIsbn = dao.search(c, "777");
            List<Book> byCategory = dao.search(c, "FICTION");

            assertEquals(1, byTitle.size());
            assertEquals(1, byAuthor.size());
            assertEquals(1, byIsbn.size());
            assertEquals(1, byCategory.size());
        }
    }

    @Test
    void searchIsResistantToSqlInjectionAttempts() throws Exception {
        try (Connection c = db.open()) {
            dao.insert(c, Book.of("888", "Safe Title", "Safe Author", "Safe", 1));

            // A classic injection payload is treated as a literal search string,
            // not as SQL, because the term is always bound as a PreparedStatement
            // parameter. It should simply match nothing rather than altering the query.
            List<Book> result = dao.search(c, "' OR '1'='1");
            assertTrue(result.isEmpty());

            List<Book> dropAttempt = dao.search(c, "'; DROP TABLE books; --");
            assertTrue(dropAttempt.isEmpty());

            // Prove the table is still intact and queryable after the attempt.
            assertEquals(1, dao.findAll(c).size());
        }
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsLiteralCharactersNotWildcards() throws Exception {
        try (Connection c = db.open()) {
            dao.insert(c, Book.of("999-A", "Has_Underscore", "Author One", "Cat", 1));
            dao.insert(c, Book.of("999-B", "HasXUnderscore", "Author Two", "Cat", 1));

            // Before the LIKE-escaping fix, "_" matched any single character, so
            // this search would have returned both books instead of just the one
            // whose title genuinely contains an underscore.
            List<Book> underscoreSearch = dao.search(c, "has_underscore");
            assertEquals(1, underscoreSearch.size());
            assertEquals("Has_Underscore", underscoreSearch.get(0).getTitle());

            dao.insert(c, Book.of("999-C", "50% Off Classics", "Author Three", "Cat", 1));
            List<Book> percentSearch = dao.search(c, "50%");
            assertEquals(1, percentSearch.size());
            assertEquals("50% Off Classics", percentSearch.get(0).getTitle());
        }
    }
}
