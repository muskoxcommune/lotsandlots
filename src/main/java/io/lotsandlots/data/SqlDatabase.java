package io.lotsandlots.data;

import java.sql.Connection;
import java.sql.SQLException;

public interface SqlDatabase {

    Connection getConnection() throws SQLException;
}