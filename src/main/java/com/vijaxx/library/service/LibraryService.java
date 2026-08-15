package com.vijaxx.library.service;

import com.vijaxx.library.dao.BookDao;
import com.vijaxx.library.dao.LoanDao;
import com.vijaxx.library.dao.MemberDao;
import com.vijaxx.library.dao.ReportDao;
import com.vijaxx.library.db.ConnectionFactory;
import com.vijaxx.library.model.Book;
import com.vijaxx.library.model.FinePolicy;
import com.vijaxx.library.model.Loan;
import com.vijaxx.library.model.Member;
import com.vijaxx.library.model.Reports;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * All library business rules. Deliberately free of Swing: every method here is
 * callable — and tested — headlessly.
 *
 * <h2>Transactions</h2>
 * Read-only calls run on an auto-commit connection. The two state-changing
 * workflows, {@link #issueBook} and {@link #returnBook}, open a single
 * connection with auto-commit off, perform every write on it, and commit only
 * once all of them succeeded. Any failure — a validation rule, a guarded UPDATE
 * that matched no rows, or a raw {@link SQLException} — rolls the whole thing
 * back, so a loan row can never exist without the matching copy decrement, and
 * vice versa.
 *
 * <h2>Clock</h2>
 * The service takes a {@link Clock}, so tests can place "today" wherever they
 * need to exercise due-date and fine boundaries without sleeping.
 */
public class LibraryService {

    private final ConnectionFactory connections;
    private final FinePolicy finePolicy;
    private final Clock clock;

    private final BookDao bookDao = new BookDao();
    private final MemberDao memberDao = new MemberDao();
    private final LoanDao loanDao = new LoanDao();
    private final ReportDao reportDao;

    public LibraryService(ConnectionFactory connections) {
        this(connections, FinePolicy.standard(), Clock.systemDefaultZone());
    }

    public LibraryService(ConnectionFactory connections, FinePolicy finePolicy, Clock clock) {
        this.connections = connections;
        this.finePolicy = finePolicy;
        this.clock = clock;
        this.reportDao = new ReportDao(finePolicy);
    }

    public FinePolicy finePolicy() {
        return finePolicy;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ------------------------------------------------------------------
    // Books
    // ------------------------------------------------------------------

    public List<Book> listBooks() {
        return read(c -> bookDao.findAll(c));
    }

    public List<Book> searchBooks(String term) {
        if (term == null || term.isBlank()) {
            return listBooks();
        }
        return read(c -> bookDao.search(c, term.trim()));
    }

    public Optional<Book> findBook(int bookId) {
        return read(c -> bookDao.findById(c, bookId));
    }

    public List<String> bookCategories() {
        return read(c -> bookDao.findCategories(c));
    }

    public Book addBook(Book book) {
        validateBook(book, true);
        return write(c -> {
            if (bookDao.findByIsbn(c, book.getIsbn()).isPresent()) {
                throw new LibraryException.InvalidOperation(
                        "A book with ISBN " + book.getIsbn() + " already exists");
            }
            bookDao.insert(c, book);
            return book;
        });
    }

    /**
     * Updates a title. {@code total_copies} may not be lowered below the number
     * of copies currently out on loan, and {@code available_copies} is recomputed
     * from the outstanding-loan count so the two can never drift.
     */
    public Book updateBook(Book book) {
        validateBook(book, false);
        return write(c -> {
            Book existing = bookDao.findById(c, book.getId())
                    .orElseThrow(() -> new LibraryException.NotFound("No book with id " + book.getId()));

            Optional<Book> byIsbn = bookDao.findByIsbn(c, book.getIsbn());
            if (byIsbn.isPresent() && byIsbn.get().getId() != book.getId()) {
                throw new LibraryException.InvalidOperation(
                        "Another book already uses ISBN " + book.getIsbn());
            }

            int out = loanDao.countOpenByBook(c, existing.getId());
            if (book.getTotalCopies() < out) {
                throw new LibraryException.InvalidOperation(
                        "Cannot set total copies to " + book.getTotalCopies()
                                + ": " + out + " copies are currently on loan");
            }
            book.setAvailableCopies(book.getTotalCopies() - out);
            bookDao.update(c, book);
            return book;
        });
    }

    /** Refuses to delete a title that has any loan history, to keep reports intact. */
    public void deleteBook(int bookId) {
        write(c -> {
            bookDao.findById(c, bookId)
                    .orElseThrow(() -> new LibraryException.NotFound("No book with id " + bookId));
            int loans = loanDao.countByBook(c, bookId);
            if (loans > 0) {
                throw new LibraryException.InvalidOperation(
                        "Cannot delete this book: it has " + loans + " loan record(s)");
            }
            bookDao.delete(c, bookId);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Members
    // ------------------------------------------------------------------

    public List<Member> listMembers() {
        return read(c -> memberDao.findAll(c));
    }

    public List<Member> searchMembers(String term) {
        if (term == null || term.isBlank()) {
            return listMembers();
        }
        return read(c -> memberDao.search(c, term.trim()));
    }

    public Optional<Member> findMember(int memberId) {
        return read(c -> memberDao.findById(c, memberId));
    }

    public Member addMember(Member member) {
        validateMember(member, true);
        return write(c -> {
            if (memberDao.findByEmail(c, member.getEmail()).isPresent()) {
                throw new LibraryException.InvalidOperation(
                        "A member with email " + member.getEmail() + " already exists");
            }
            memberDao.insert(c, member);
            return member;
        });
    }

    public Member updateMember(Member member) {
        validateMember(member, false);
        return write(c -> {
            memberDao.findById(c, member.getId())
                    .orElseThrow(() -> new LibraryException.NotFound("No member with id " + member.getId()));
            Optional<Member> byEmail = memberDao.findByEmail(c, member.getEmail());
            if (byEmail.isPresent() && byEmail.get().getId() != member.getId()) {
                throw new LibraryException.InvalidOperation(
                        "Another member already uses email " + member.getEmail());
            }
            memberDao.update(c, member);
            return member;
        });
    }

    /** Refuses to delete a member with any loan history. */
    public void deleteMember(int memberId) {
        write(c -> {
            memberDao.findById(c, memberId)
                    .orElseThrow(() -> new LibraryException.NotFound("No member with id " + memberId));
            int loans = loanDao.countByMember(c, memberId);
            if (loans > 0) {
                throw new LibraryException.InvalidOperation(
                        "Cannot delete this member: they have " + loans + " loan record(s)");
            }
            memberDao.delete(c, memberId);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Borrowing workflow
    // ------------------------------------------------------------------

    /**
     * Issues one copy of {@code bookId} to {@code memberId}, atomically.
     *
     * <p>Order of checks:
     * <ol>
     *   <li>book and member must exist; the member must be active</li>
     *   <li>the member must not already hold a copy of this same title</li>
     *   <li>outstanding loans must be below the tier's borrow limit</li>
     *   <li>the guarded decrement of {@code available_copies} must match a row —
     *       if it matches none, every copy is out and the transaction rolls back</li>
     * </ol>
     */
    public Loan issueBook(int bookId, int memberId) {
        return write(c -> {
            Book book = bookDao.findById(c, bookId)
                    .orElseThrow(() -> new LibraryException.NotFound("No book with id " + bookId));
            Member member = memberDao.findById(c, memberId)
                    .orElseThrow(() -> new LibraryException.NotFound("No member with id " + memberId));

            if (!member.isActive()) {
                throw new LibraryException.InvalidOperation(
                        "Member " + member.getName() + " is not active");
            }

            if (loanDao.findOpenByBookAndMember(c, bookId, memberId).isPresent()) {
                throw new LibraryException.InvalidOperation(
                        member.getName() + " already has a copy of \"" + book.getTitle() + "\" out");
            }

            int held = loanDao.countOpenByMember(c, memberId);
            int limit = member.getMembershipType().borrowLimit();
            if (held >= limit) {
                throw new LibraryException.BorrowLimitExceeded(
                        member.getName() + " already holds " + held + " book(s); the "
                                + member.getMembershipType().label() + " limit is " + limit,
                        limit, held);
            }

            // Guarded decrement: the WHERE clause enforces available_copies >= 1.
            if (bookDao.adjustAvailableCopies(c, bookId, -1) != 1) {
                throw new LibraryException.NoCopiesAvailable(
                        "No copies of \"" + book.getTitle() + "\" are available");
            }

            LocalDate issueDate = today();
            LocalDate dueDate = issueDate.plusDays(member.getMembershipType().loanPeriodDays());
            Loan loan = new Loan(0, bookId, memberId, issueDate, dueDate, null, BigDecimal.ZERO);
            loanDao.insert(c, loan);
            return loan;
        });
    }

    /** Returns the copy attached to {@code loanId}. */
    public ReturnReceipt returnBook(int loanId) {
        return write(c -> {
            Loan loan = loanDao.findById(c, loanId)
                    .orElseThrow(() -> new LibraryException.NotFound("No loan with id " + loanId));
            return closeLoan(c, loan);
        });
    }

    /** Convenience for the UI: return whichever copy of this title the member holds. */
    public ReturnReceipt returnBook(int bookId, int memberId) {
        return write(c -> {
            Loan loan = loanDao.findOpenByBookAndMember(c, bookId, memberId)
                    .orElseThrow(() -> new LibraryException.NotFound(
                            "No outstanding loan of book " + bookId + " for member " + memberId));
            return closeLoan(c, loan);
        });
    }

    private ReturnReceipt closeLoan(Connection c, Loan loan) throws SQLException {
        if (loan.isReturned()) {
            throw new LibraryException.InvalidOperation(
                    "Loan " + loan.getId() + " was already returned on " + loan.getReturnDate());
        }

        LocalDate returnDate = today();
        long daysLate = FinePolicy.daysLate(loan.getDueDate(), returnDate);
        BigDecimal fine = finePolicy.fineForDaysLate(daysLate);

        if (!loanDao.markReturned(c, loan.getId(), returnDate, fine)) {
            // Someone else closed it between our read and our write.
            throw new LibraryException.InvalidOperation(
                    "Loan " + loan.getId() + " was already returned by another operation");
        }
        if (bookDao.adjustAvailableCopies(c, loan.getBookId(), +1) != 1) {
            // Would push available_copies above total_copies — data is inconsistent.
            throw new LibraryException.InvalidOperation(
                    "Could not restore the copy count for book " + loan.getBookId());
        }

        Book book = bookDao.findById(c, loan.getBookId()).orElseThrow();
        Member member = memberDao.findById(c, loan.getMemberId()).orElseThrow();
        return new ReturnReceipt(loan.getId(), book.getId(), book.getTitle(),
                member.getId(), member.getName(), loan.getDueDate(), returnDate, daysLate, fine);
    }

    public List<Loan> openLoans() {
        return read(c -> loanDao.findOpen(c));
    }

    public List<Loan> openLoansOf(int memberId) {
        return read(c -> loanDao.findOpenByMember(c, memberId));
    }

    public int openLoanCount(int memberId) {
        return read(c -> loanDao.countOpenByMember(c, memberId));
    }

    public Optional<Loan> findLoan(int loanId) {
        return read(c -> loanDao.findById(c, loanId));
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    public List<Reports.LoanRow> currentlyBorrowed() {
        return read(c -> reportDao.currentlyBorrowed(c, today()));
    }

    public List<Reports.LoanRow> overdueLoans() {
        return read(c -> reportDao.overdue(c, today()));
    }

    public List<Reports.LoanRow> memberHistory(int memberId) {
        return read(c -> reportDao.loanHistoryForMember(c, memberId, today()));
    }

    public List<Reports.TitlePopularity> mostBorrowed(int limit) {
        return read(c -> reportDao.mostBorrowed(c, limit));
    }

    public List<Reports.MemberActivity> memberActivity() {
        return read(c -> reportDao.memberActivity(c, today()));
    }

    public BigDecimal totalFinesCollected() {
        return read(c -> reportDao.totalFinesCollected(c));
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private static void validateBook(Book book, boolean isNew) {
        require(book != null, "Book is required");
        require(notBlank(book.getIsbn()), "ISBN is required");
        require(notBlank(book.getTitle()), "Title is required");
        require(notBlank(book.getAuthor()), "Author is required");
        require(notBlank(book.getCategory()), "Category is required");
        require(book.getTotalCopies() >= 0, "Total copies cannot be negative");
        if (isNew) {
            require(book.getAvailableCopies() >= 0 && book.getAvailableCopies() <= book.getTotalCopies(),
                    "Available copies must be between 0 and the total");
        }
    }

    private static void validateMember(Member member, boolean isNew) {
        require(member != null, "Member is required");
        require(notBlank(member.getName()), "Name is required");
        require(notBlank(member.getEmail()) && member.getEmail().contains("@"),
                "A valid email is required");
        require(member.getMembershipType() != null, "Membership type is required");
        require(member.getJoinDate() != null, "Join date is required");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new LibraryException.InvalidOperation(message);
        }
    }

    // ------------------------------------------------------------------
    // Connection / transaction plumbing
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface DbAction<T> {
        T run(Connection c) throws SQLException;
    }

    /** Read-only unit of work on an auto-commit connection. */
    private <T> T read(DbAction<T> action) {
        try (Connection c = connections.open()) {
            return action.run(c);
        } catch (SQLException e) {
            throw new LibraryException.DataAccess("Database read failed: " + e.getMessage(), e);
        }
    }

    /**
     * Read-write unit of work in a single transaction. Commits on success;
     * rolls back on <em>any</em> throwable, including our own business-rule
     * exceptions thrown after a write has already happened.
     */
    private <T> T write(DbAction<T> action) {
        Connection c = null;
        try {
            c = connections.open();
            c.setAutoCommit(false);
            try {
                T result = action.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                safeRollback(c);
                throw e;
            }
        } catch (SQLException e) {
            throw new LibraryException.DataAccess("Database write failed: " + e.getMessage(), e);
        } finally {
            closeQuietly(c);
        }
    }

    private static void safeRollback(Connection c) {
        try {
            if (c != null) {
                c.rollback();
            }
        } catch (SQLException ignored) {
            // Nothing useful to do; the original failure is what the caller sees.
        }
    }

    private static void closeQuietly(Connection c) {
        try {
            if (c != null) {
                c.setAutoCommit(true);
                c.close();
            }
        } catch (SQLException ignored) {
            // Connection is being discarded anyway.
        }
    }
}
