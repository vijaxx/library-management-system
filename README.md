# Library Management System

A desktop library management application written in Java: Swing for the UI,
plain JDBC (no ORM) for persistence, H2 as the embedded database. Built as a
portfolio piece — the borrowing/return workflow is transactional and the whole
business layer is covered by a headless JUnit 5 suite.

## What it does

- **Books** — CRUD on the catalog: ISBN, title, author, category, total vs.
  available copy counts. Search across all fields.
- **Members** — CRUD on members: name, email, phone, membership tier, join
  date, active flag. Each tier fixes a borrow limit and a loan period:

  | Tier     | Borrow limit | Loan period |
  |----------|-------------:|------------:|
  | Student  | 3 books      | 14 days     |
  | Standard | 5 books      | 21 days     |
  | Premium  | 10 books     | 30 days     |

- **Borrowing** — issue a copy to a member (enforces the tier's borrow limit,
  refuses if no copy is available, refuses a second copy of the same title to
  the same member) and return a copy (restores the count, computes an overdue
  fine).
- **Reports** — currently-borrowed list, overdue list with accruing fines,
  most-borrowed titles, per-member activity — each a real multi-table
  `JOIN`/`GROUP BY` query, not client-side aggregation.

## Layering

```
ui/        Swing components. Talks only to service/, never to dao/ or db/.
service/   LibraryService — all business rules, transaction boundaries.
dao/       BookDao, MemberDao, LoanDao, ReportDao — PreparedStatement SQL.
db/        Database (H2 bootstrap), ConnectionFactory, SqlScriptRunner.
model/     Plain data types: Book, Member, Loan, MembershipType, FinePolicy,
           Reports (report row records).
```

Each layer only depends on the one below it. `LibraryService` is the single
place that opens a `Connection` and decides whether to commit or roll back;
the DAOs take a `Connection` as a parameter and never open their own, which is
what lets the service compose several DAO calls into one transaction.

`LibraryService` depends on the `ConnectionFactory` interface, not on H2 or on
`Database` directly, and it takes a `java.time.Clock` instead of calling
`LocalDate.now()`. Both are there for testability: the test suite hands it an
in-memory H2 database and a fixed/mutable clock, so fine calculations across
day boundaries don't require sleeping in a test.

## Swing threading discipline (EDT / SwingWorker)

This is the Swing correctness point most student projects get wrong, so it's
worth spelling out:

- `Main.main()` runs on the JVM's main thread, does the database bootstrap
  there (fast local file I/O, happens once, before any UI exists), and then
  calls `SwingUtilities.invokeLater(...)` to build `MainWindow`. **No Swing
  component is created before that hand-off.**
- Every panel constructor (`BooksPanel`, `MembersPanel`, `BorrowingPanel`,
  `ReportsPanel`) runs entirely on the EDT — building labels, tables, buttons —
  and then immediately kicks off a background load through `BackgroundTask`.
- `BackgroundTask.run(...)` is the single choke point where the app talks to
  `LibraryService`. It wraps a `SwingWorker`: `doInBackground()` runs on a
  worker thread and is the *only* place a service/DAO call is made;
  `done()` runs back on the EDT and is the only place the result is applied to
  a table model or a dialog. No panel ever calls `LibraryService` directly
  from an `ActionListener` — it always goes through `BackgroundTask`, so a
  slow query never freezes the window.
- `ObjectTableModel` (the shared `AbstractTableModel`) is only ever mutated
  through `setRows(List<T>)`, and that method is only ever called from inside
  a `BackgroundTask` success callback — i.e., on the EDT.

## The transactional borrowing/return logic

`LibraryService.issueBook` and `LibraryService.returnBook` are the core of the
app. Each opens one JDBC connection, turns off auto-commit, does all of its
reads and writes on that single connection, and either commits everything or
rolls back everything:

**Issue** (`LibraryService.issueBook`):
1. Look up the book and the member; the member must be active.
2. Reject if the member already holds a copy of this exact title.
3. Reject if the member's open-loan count is already at their tier's limit
   (`BorrowLimitExceeded`).
4. Attempt a *guarded* decrement of `available_copies`:
   `UPDATE books SET available_copies = available_copies + (-1) WHERE id = ?
   AND available_copies + (-1) >= 0` — if no copy is free, this `UPDATE`
   matches zero rows, and the service throws `NoCopiesAvailable` and rolls
   back. The invariant (never negative, never above `total_copies`) lives in
   the `WHERE` clause itself, not just in application code, which is what
   makes a half-applied issue impossible even under concurrent access.
5. Insert the `loans` row with `issue_date = today`, `due_date = today +
   tier.loanPeriodDays()`.
6. Commit.

If any step after the first write throws — including a raw `SQLException` —
the `write()` helper in `LibraryService` catches it, calls
`connection.rollback()`, and rethrows, so no row is left half-written. This is
exercised directly in `TransactionRollbackTest`, which injects a failure
*after* the copy decrement and verifies the decrement was undone.

**Return** (`LibraryService.returnBook`):
1. Look up the loan; reject if it was already returned.
2. Compute `days_late = max(0, return_date - due_date)` and the fine from
   `FinePolicy`.
3. Close the loan with a guarded `UPDATE ... WHERE id = ? AND return_date IS
   NULL` (so a duplicate/concurrent return affects zero rows instead of
   double-crediting the copy count).
4. Increment `available_copies` by 1 (also guarded, so it can't exceed
   `total_copies`).
5. Commit.

## Fine calculation rules

`FinePolicy` (`model/FinePolicy.java`) is the single source of truth, used by
both the return workflow and the overdue report so they can't drift apart:

```
days_late = max(0, days between due_date and (return_date, or today if still out))
fine      = days_late * rate_per_day        (rate_per_day = 5.00 by default)
```

- Returning **on** the due date is 0 days late → **zero fine**.
- Returning **early** is 0 days late → **zero fine**.
- **1 day late** → exactly one day's rate.
- Any longer delay scales linearly with whole days late.
- Money is rounded half-up to 2 decimal places at every step.

These boundaries (0 days, 1 day, many days) are each their own test in
`FinePolicyTest` and `LibraryServiceReturnTest`.

## Data access layer

Every DAO method (`BookDao`, `MemberDao`, `LoanDao`, `ReportDao`) uses a
`PreparedStatement` with bound parameters — there is no string-concatenated
SQL anywhere in the codebase, including the search/filter queries. This is
checked directly: `BookDaoTest.searchIsResistantToSqlInjectionAttempts` runs
payloads like `' OR '1'='1` and `'; DROP TABLE books; --` through
`BookDao.search` and asserts they match nothing and the table survives intact.

## Build and run

Requires JDK 17+ and Maven. This machine uses Homebrew's keg-only OpenJDK, so
every command is prefixed with:

```bash
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"
java -version   # sanity check
mvn -version
```

Run the test suite:

```bash
mvn clean test
```

Build a runnable, dependency-shaded jar:

```bash
mvn clean package
java -jar target/library-management-system.jar
```

On first run the app creates `data/library.mv.db` next to wherever you run it
from, loads the schema, and seeds it with 18 books and 6 members so the UI has
something to look at immediately — no setup step required. Delete the `data/`
folder to reset.

Or run straight from Maven during development:

```bash
mvn exec:java
```

## Database: H2 instead of MySQL

**This machine has no MySQL server, and the brief explicitly says not to
install one.** The app uses [H2](https://www.h2database.com/), an embedded
Java database, in file mode with `AUTO_SERVER=TRUE`. Practically, this means:

- No server process to install, configure, or start — the "database" is a
  single `.mv.db` file created automatically on first run.
- No credentials to manage (`sa` / empty password, embedded-only).
- The SQL used (`CREATE TABLE`, `CHECK` constraints, `AUTO_INCREMENT`,
  `PreparedStatement`, transactions, `DATEDIFF`) is close enough to standard
  SQL/MySQL that porting the DAOs to a real MySQL `DataSource` would mean
  changing the JDBC URL in `Database` and a couple of dialect-specific bits
  (H2's `LIMIT` syntax and `DATEDIFF('DAY', a, b)` vs. MySQL's `DATEDIFF(b,
  a)`), not rewriting the DAO layer.
- Tests use a separate, unique in-memory H2 instance per test class
  (`Database.inMemory()`), so the suite never touches the file the app itself
  writes to and tests can't interfere with each other.

This is a substitution made for portability of this portfolio piece, not a
architectural recommendation — a real deployment with concurrent users would
use MySQL/PostgreSQL with a connection pool.

## What was verified, and how

- **`mvn clean test` passes**: 44 tests, 0 failures, 0 errors (see below for
  the breakdown). This is a real run of the build, not a claim.
- **The app compiles**: `mvn clean compile` and `mvn clean package` both
  succeed and produce a runnable shaded jar
  (`target/library-management-system.jar`).
- **The app launches and reaches the GUI**: launched with
  `java -Djava.awt.headless=false -jar target/library-management-system.jar`
  on this machine's real (non-headless) environment. It ran for several
  seconds without throwing, and — more concretely — it created
  `data/library.mv.db`, which only happens if `Main.main()` completed the
  database bootstrap and handed off to the EDT successfully. The process was
  then terminated manually (not via the window's close button).
- **What was *not* verified**: no mouse clicks, no visual inspection of any
  dialog, table, or button, and no confirmation that a specific pixel renders
  correctly. This environment cannot drive a GUI, so nothing about button
  layout, dialog behavior, or on-screen appearance was checked by hand — only
  through the behavior of the underlying `LibraryService` calls the UI code
  invokes, which the JUnit suite does cover. If you clone this and run it
  yourself, that's the first real visual test it will get.
- Running the jar with `-Djava.awt.headless=true` (true headless, no display
  at all) correctly raises `java.awt.HeadlessException` when Swing tries to
  create the window — expected behavior for any AWT/Swing app, included here
  only to show the database bootstrap step (which runs *before* any Swing
  code) completes cleanly even without a display.

### Test breakdown (44 tests, `mvn clean test`)

| Class | Tests | Covers |
|---|---:|---|
| `FinePolicyTest` | 7 | Fine boundaries: on-time, early, 1 day late, many days late, zero-days-negative clamp, rate validation |
| `BookDaoTest` | 8 | CRUD, ISBN lookup, guarded copy-count updates (can't go negative or over total), search, **SQL-injection resistance** |
| `MemberDaoTest` | 5 | CRUD, case-insensitive email/search lookups |
| `LibraryServiceBorrowingTest` | 9 | Copy decrement on issue, no-copies-available rejection, **borrow-limit enforcement per tier**, duplicate-title rejection, inactive-member rejection, not-found handling |
| `LibraryServiceReturnTest` | 7 | Fine-free on-time/early return, 1-day and many-day fines, copy restoration, double-return rejection, return-by-book-and-member |
| `TransactionRollbackTest` | 2 | A rejected issue leaves no trace; a mid-transaction failure rolls back an already-applied copy decrement |
| `LibraryServiceReportsTest` | 6 | Open-loan filtering, overdue aggregation with fines, most-borrowed ranking (including zero-borrow titles), member-activity aggregation, total-fines aggregation |

## Known limitations

- H2 instead of MySQL (see above) — an intentional, documented substitution
  for this environment.
- No authentication/authorization — this is a single-operator desktop tool,
  not a multi-user system.
- No visual/manual QA of the Swing UI was possible in the build environment;
  see "What was verified" above.
- `AUTO_SERVER=TRUE` allows a second local process to connect to the same H2
  file concurrently for convenience during development; it is not a
  substitute for a real multi-client database server under load.
