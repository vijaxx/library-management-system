package com.vijaxx.library.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hands out JDBC connections. Everything above the DAO layer depends on this
 * interface rather than on H2, so tests can swap in a throwaway in-memory
 * database (or a deliberately broken factory) without touching production code.
 */
@FunctionalInterface
public interface ConnectionFactory {

    /** A new connection with auto-commit left at the JDBC default (true). */
    Connection open() throws SQLException;
}
