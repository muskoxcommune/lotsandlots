package io.lotsandlots.data;

import com.typesafe.config.Config;
import io.lotsandlots.util.ConfigWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteDatabase {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final Logger LOG = LoggerFactory.getLogger(SqliteDatabase.class);

    private static SqliteDatabase DB;

    private final String url;
    private final Connection conn;

    public SqliteDatabase(String url) throws SQLException {
        this.url = url;
        //try (Connection conn = DriverManager.getConnection(url)) {
        //    DatabaseMetaData meta = conn.getMetaData();
        //    LOG.info("Initialized SqliteDatabase:{} using {}", url, meta.getDriverName());
        //} catch (SQLException e) {
        //    LOG.error("Failed to initialized SqliteDatabase:{}", url, e);
        //}
        conn = DriverManager.getConnection(url);
    }

    public static SqliteDatabase getInstance() {
        if (DB == null) {
            String url = CONFIG.hasPath("data.url")
                    ? CONFIG.getString("data.url")
                    : "jdbc:sqlite:sqlite.db";
            try {
                DB = new SqliteDatabase(url);
            } catch (SQLException e) {
                LOG.error("Failed to initialized SqliteDatabase:{}", url, e);
            }
        }
        return DB;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public synchronized void executeSql(String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void executePreparedQuery(String sql, PreparedStatementCallback callback) throws SQLException {
        try (Connection c = DriverManager.getConnection(url); PreparedStatement stmt = c.prepareStatement(sql)) {
            callback.call(stmt);
            stmt.executeQuery();
        }
    }

    public synchronized void executePreparedUpdate(String sql, PreparedStatementCallback callback) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            callback.call(stmt);
            stmt.executeUpdate();
        }
    }

    public interface PreparedStatementCallback {
        void call(PreparedStatement stmt) throws SQLException;
    }
}