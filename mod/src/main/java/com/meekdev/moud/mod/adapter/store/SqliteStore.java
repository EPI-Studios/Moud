package com.meekdev.moud.mod.adapter.store;

import com.meekdev.moud.script.api.StoreRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

// store on a sqlite file in the world folder: one table of values, one of leases
public final class SqliteStore implements StoreRef {

    private final Supplier<@Nullable Path> folder;
    // fresh each run, so a lease this server held before a crash is not mistaken for its own
    private final String owner = UUID.randomUUID().toString();
    private @Nullable Connection connection;

    public SqliteStore(Supplier<@Nullable Path> folder) {
        this.folder = folder;
    }

    private Connection open() {
        if (connection != null) return connection;
        Path dir = folder.get();
        if (dir == null) throw new IllegalStateException("store is only there while a world is running");
        try {
            Files.createDirectories(dir);
            connection = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("moud.db"));
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS entries (store TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY (store, key))");
                s.execute("CREATE TABLE IF NOT EXISTS leases (store TEXT NOT NULL, key TEXT NOT NULL, owner TEXT NOT NULL, expires INTEGER NOT NULL, PRIMARY KEY (store, key))");
            }
            return connection;
        } catch (Exception e) {
            connection = null;
            throw new IllegalStateException("the save file could not be opened: " + e.getMessage(), e);
        }
    }

    @Override
    public @Nullable String get(String store, String key) {
        try (PreparedStatement q = open().prepareStatement("SELECT value FROM entries WHERE store = ? AND key = ?")) {
            q.setString(1, store);
            q.setString(2, key);
            try (ResultSet row = q.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        } catch (SQLException e) {
            throw failed(e);
        }
    }

    @Override
    public void set(String store, String key, @Nullable String json) {
        try {
            write(open(), store, key, json);
        } catch (SQLException e) {
            throw failed(e);
        }
    }

    @Override
    public @Nullable String update(String store, String key, UnaryOperator<String> change) {
        Connection db = open();
        try {
            try (Statement s = db.createStatement()) {
                s.execute("BEGIN IMMEDIATE");
            }
            try {
                String next = change.apply(get(store, key));
                write(db, store, key, next);
                try (Statement s = db.createStatement()) {
                    s.execute("COMMIT");
                }
                return next;
            } catch (RuntimeException | SQLException e) {
                try (Statement s = db.createStatement()) {
                    s.execute("ROLLBACK");
                }
                throw e;
            }
        } catch (SQLException e) {
            throw failed(e);
        }
    }

    private static void write(Connection db, String store, String key, @Nullable String json) throws SQLException {
        if (json == null) {
            try (PreparedStatement q = db.prepareStatement("DELETE FROM entries WHERE store = ? AND key = ?")) {
                q.setString(1, store);
                q.setString(2, key);
                q.executeUpdate();
            }
            return;
        }
        try (PreparedStatement q = db.prepareStatement(
                "INSERT INTO entries (store, key, value) VALUES (?, ?, ?) ON CONFLICT (store, key) DO UPDATE SET value = excluded.value")) {
            q.setString(1, store);
            q.setString(2, key);
            q.setString(3, json);
            q.executeUpdate();
        }
    }

    @Override
    public List<String> keys(String store, String prefix, int limit) {
        try (PreparedStatement q = open().prepareStatement(
                "SELECT key FROM entries WHERE store = ? AND substr(key, 1, ?) = ? ORDER BY key LIMIT ?")) {
            q.setString(1, store);
            q.setInt(2, prefix.length());
            q.setString(3, prefix);
            q.setInt(4, Math.max(0, limit));
            List<String> keys = new ArrayList<>();
            try (ResultSet row = q.executeQuery()) {
                while (row.next()) keys.add(row.getString(1));
            }
            return keys;
        } catch (SQLException e) {
            throw failed(e);
        }
    }

    @Override
    public boolean lease(String store, String key, String who, double seconds) {
        long now = System.currentTimeMillis();
        try (PreparedStatement q = open().prepareStatement(
                "INSERT INTO leases (store, key, owner, expires) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (store, key) DO UPDATE SET owner = excluded.owner, expires = excluded.expires "
                        + "WHERE leases.owner = excluded.owner OR leases.expires < ?")) {
            q.setString(1, store);
            q.setString(2, key);
            q.setString(3, who);
            q.setLong(4, now + (long) (seconds * 1000));
            q.setLong(5, now);
            return q.executeUpdate() > 0;
        } catch (SQLException e) {
            throw failed(e);
        }
    }

    @Override
    public void release(String store, String key, String who) {
        try (PreparedStatement q = open().prepareStatement("DELETE FROM leases WHERE store = ? AND key = ? AND owner = ?")) {
            q.setString(1, store);
            q.setString(2, key);
            q.setString(3, who);
            q.executeUpdate();
        } catch (SQLException e) {
            throw failed(e);
        }
    }

    @Override
    public String owner() {
        return owner;
    }

    public void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
            // closing on the way out has nobody left to tell
        }
        connection = null;
    }

    private static IllegalStateException failed(SQLException e) {
        return new IllegalStateException("the save file refused that: " + e.getMessage(), e);
    }
}
