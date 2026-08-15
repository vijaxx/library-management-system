package com.vijaxx.library.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal ";"-delimited SQL script executor used to load {@code schema.sql} and
 * {@code seed.sql} from the classpath. It is quote-aware, so a semicolon inside
 * a string literal does not split a statement, and it strips {@code --} comments.
 */
public final class SqlScriptRunner {

    private SqlScriptRunner() {
    }

    /** Loads a classpath resource and executes every statement it contains. */
    public static void runResource(Connection connection, String resourceName) throws SQLException {
        for (String sql : parse(readResource(resourceName))) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    static String readResource(String resourceName) {
        ClassLoader loader = SqlScriptRunner.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("SQL resource not found on classpath: " + resourceName);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resourceName, e);
        }
    }

    /** Splits a script into individual statements, honouring single-quoted literals. */
    static List<String> parse(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;

        int i = 0;
        while (i < script.length()) {
            char c = script.charAt(i);

            if (!inString && c == '-' && i + 1 < script.length() && script.charAt(i + 1) == '-') {
                while (i < script.length() && script.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            if (c == '\'') {
                // '' inside a literal is an escaped quote and keeps us in the string.
                if (inString && i + 1 < script.length() && script.charAt(i + 1) == '\'') {
                    current.append("''");
                    i += 2;
                    continue;
                }
                inString = !inString;
                current.append(c);
                i++;
                continue;
            }

            if (c == ';' && !inString) {
                addIfNotBlank(statements, current);
                current.setLength(0);
                i++;
                continue;
            }

            current.append(c);
            i++;
        }
        addIfNotBlank(statements, current);
        return statements;
    }

    private static void addIfNotBlank(List<String> target, StringBuilder buffer) {
        String sql = buffer.toString().trim();
        if (!sql.isEmpty()) {
            target.add(sql);
        }
    }
}
