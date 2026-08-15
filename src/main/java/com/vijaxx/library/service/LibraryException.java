package com.vijaxx.library.service;

/**
 * Base type for every failure the service layer reports. Unchecked, so the
 * Swing layer can let it bubble out of a {@code SwingWorker} background task and
 * catch it once in {@code done()}.
 */
public class LibraryException extends RuntimeException {

    public LibraryException(String message) {
        super(message);
    }

    public LibraryException(String message, Throwable cause) {
        super(message, cause);
    }

    /** A record the caller asked for does not exist. */
    public static class NotFound extends LibraryException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** The member already holds as many books as their tier allows. */
    public static class BorrowLimitExceeded extends LibraryException {
        private final int limit;
        private final int currentlyHeld;

        public BorrowLimitExceeded(String message, int limit, int currentlyHeld) {
            super(message);
            this.limit = limit;
            this.currentlyHeld = currentlyHeld;
        }

        public int limit() { return limit; }
        public int currentlyHeld() { return currentlyHeld; }
    }

    /** Every copy of the title is already out on loan. */
    public static class NoCopiesAvailable extends LibraryException {
        public NoCopiesAvailable(String message) {
            super(message);
        }
    }

    /** The operation contradicts the current state (inactive member, already returned, ...). */
    public static class InvalidOperation extends LibraryException {
        public InvalidOperation(String message) {
            super(message);
        }
    }

    /** A {@code SQLException} or other storage failure, wrapped. */
    public static class DataAccess extends LibraryException {
        public DataAccess(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
