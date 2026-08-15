package com.vijaxx.library.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * H2 {@link ConnectionFactory} plus first-run schema/seed bootstrap.
 *
 * <p>H2 is embedded: the whole database is a file next to the application (or
 * purely in memory for tests), so the app runs after a clone with no server to
 * install and no credentials to configure.
 */
public final class Database implements ConnectionFactory {

    private static final AtomicInteger MEMORY_DB_COUNTER = new AtomicInteger();

    /** Default on-disk location, relative to the working directory. */
    public static final Path DEFAULT_FILE = Path.of("data", "library");

    private final String jdbcUrl;
    private final String user;
    private final String password;

    private Database(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    /**
     * File-backed database. {@code path} is the H2 base name; H2 appends
     * {@code .mv.db} itself.
     */
    public static Database file(Path path) {
        String absolute = path.toAbsolutePath().toString();
        return new Database("jdbc:h2:" + absolute + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    /** The database the desktop app uses. */
    public static Database defaultFile() {
        return file(DEFAULT_FILE);
    }

    /**
     * A private in-memory database, unique per call. {@code DB_CLOSE_DELAY=-1}
     * keeps it alive between connections for the lifetime of the JVM, which is
     * what the tests need.
     */
    public static Database inMemory() {
        String name = "lms_test_" + MEMORY_DB_COUNTER.incrementAndGet() + "_" + System.nanoTime();
        return new Database("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    /**
     * Creates the schema if missing and loads demo rows when the library is
     * empty. Safe to call on every start.
     *
     * @return this, for chaining
     */
    public Database initialize() {
        return initialize(true);
    }

    /**
     * @param withSeedData when false the schema is created but no demo rows are inserted
     */
    public Database initialize(boolean withSeedData) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                SqlScriptRunner.runResource(connection, "schema.sql");
                if (withSeedData && isEmpty(connection)) {
                    SqlScriptRunner.runResource(connection, "seed.sql");
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialise database at " + jdbcUrl, e);
        }
        return this;
    }

    private static boolean isEmpty(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM books")) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }
}
