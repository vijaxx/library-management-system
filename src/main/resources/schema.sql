-- Library Management System schema (H2, ANSI-ish SQL)
-- Executed once on first run; every statement is idempotent.

CREATE TABLE IF NOT EXISTS books (
    id              INTEGER      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    isbn            VARCHAR(20)  NOT NULL UNIQUE,
    title           VARCHAR(255) NOT NULL,
    author          VARCHAR(255) NOT NULL,
    category        VARCHAR(100) NOT NULL,
    total_copies    INTEGER      NOT NULL,
    available_copies INTEGER     NOT NULL,
    CONSTRAINT chk_books_total     CHECK (total_copies >= 0),
    CONSTRAINT chk_books_available CHECK (available_copies >= 0),
    CONSTRAINT chk_books_bounds    CHECK (available_copies <= total_copies)
);

CREATE TABLE IF NOT EXISTS members (
    id              INTEGER      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(30),
    membership_type VARCHAR(20)  NOT NULL,
    join_date       DATE         NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS loans (
    id          INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
    book_id     INTEGER NOT NULL,
    member_id   INTEGER NOT NULL,
    issue_date  DATE    NOT NULL,
    due_date    DATE    NOT NULL,
    return_date DATE,
    fine        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT fk_loans_book   FOREIGN KEY (book_id)   REFERENCES books(id),
    CONSTRAINT fk_loans_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE INDEX IF NOT EXISTS idx_loans_member  ON loans(member_id);
CREATE INDEX IF NOT EXISTS idx_loans_book    ON loans(book_id);
CREATE INDEX IF NOT EXISTS idx_loans_open    ON loans(return_date);
CREATE INDEX IF NOT EXISTS idx_books_title   ON books(title);
CREATE INDEX IF NOT EXISTS idx_members_name  ON members(name);
