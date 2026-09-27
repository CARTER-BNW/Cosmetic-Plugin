package com.jbac.cosmetics.db;

import org.slf4j.Logger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * One SQLite connection, one worker thread. Every access after open() goes through the executor, so
 * there is never a SQLITE_BUSY between our own writes, and the main thread never blocks on disk.
 */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection c) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection c) throws SQLException;
    }

    /** Index = schema version - 1. Append only; never edit a shipped migration. */
    private static final List<List<String>> MIGRATIONS = List.of(
            List.of(
                    """
                    CREATE TABLE IF NOT EXISTS purchases (
                      txn_id      TEXT PRIMARY KEY,
                      player_uuid TEXT NOT NULL,
                      item_id     TEXT NOT NULL,
                      price       INTEGER NOT NULL,
                      status      TEXT NOT NULL,
                      created_at  INTEGER NOT NULL,
                      updated_at  INTEGER NOT NULL)""",
                    "CREATE INDEX IF NOT EXISTS idx_purchases_player ON purchases(player_uuid, status)",
                    """
                    CREATE TABLE IF NOT EXISTS coin_ledger (
                      id          INTEGER PRIMARY KEY AUTOINCREMENT,
                      player_uuid TEXT,
                      amount      INTEGER NOT NULL,
                      kind        TEXT NOT NULL,
                      source      TEXT NOT NULL,
                      ext_txn     TEXT UNIQUE,
                      ref         TEXT,
                      created_at  INTEGER NOT NULL)""",
                    """
                    CREATE TABLE IF NOT EXISTS pending_deliveries (
                      id          INTEGER PRIMARY KEY AUTOINCREMENT,
                      player_uuid TEXT NOT NULL,
                      amount      INTEGER NOT NULL,
                      reason      TEXT NOT NULL,
                      created_at  INTEGER NOT NULL)""",
                    "CREATE INDEX IF NOT EXISTS idx_deliveries_player ON pending_deliveries(player_uuid)"
            )
    );

    private final File file;
    private final Logger log;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CosmeticPlugin-DB");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;
    private int schemaVersion;

    public Database(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** Opens the file and applies pending migrations. Called once from onEnable, on the main thread. */
    public void open() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC"); // plugin classloaders do not auto-register JDBC drivers
        file.getParentFile().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA foreign_keys=ON");
        }
        migrate();
    }

    private void migrate() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            int current = 0;
            try (ResultSet rs = s.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
                if (rs.next()) {
                    current = rs.getInt(1);
                } else {
                    s.execute("INSERT INTO schema_version(version) VALUES (0)");
                }
            }
            for (int v = current + 1; v <= MIGRATIONS.size(); v++) {
                log.info("Applying database migration v{}", v);
                connection.setAutoCommit(false);
                try {
                    for (String ddl : MIGRATIONS.get(v - 1)) {
                        s.execute(ddl);
                    }
                    s.execute("UPDATE schema_version SET version = " + v);
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
            schemaVersion = MIGRATIONS.size();
        }
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    /** Runs a read on the DB thread. */
    public <T> CompletableFuture<T> query(SqlFunction<T> fn) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fn.apply(connection);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /** Runs a write on the DB thread with auto-commit. */
    public CompletableFuture<Void> execute(SqlConsumer fn) {
        return query(c -> {
            fn.accept(c);
            return null;
        });
    }

    /** Runs several statements atomically on the DB thread. */
    public <T> CompletableFuture<T> transaction(SqlFunction<T> fn) {
        return query(c -> {
            c.setAutoCommit(false);
            try {
                T result = fn.apply(c);
                c.commit();
                return result;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        });
    }

    /** Lets queued writes finish (up to 10 s) before the connection closes. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Database worker did not finish within 10s; some writes may be lost");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("Error closing database", e);
        }
    }
}
