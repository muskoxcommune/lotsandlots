package io.lotsandlots.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqliteDatabase implements SqlDatabase {

    private static final Logger LOG = LoggerFactory.getLogger(SqliteDatabase.class);

    private final String url;

    public SqliteDatabase(String url) {
        this.url = url;
        try (Connection conn = DriverManager.getConnection(url)) {
            DatabaseMetaData meta = conn.getMetaData();
            LOG.info("Initialized SqliteDatabase:{} using {}", url, meta.getDriverName());
        } catch (SQLException e) {
            LOG.error("Failed to initialized SqliteDatabase:{}", url, e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }
}
