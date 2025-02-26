package org.example;

import java.sql.*;

public class DBManager {
    private static final String URL = "jdbc:sqlite:expenses.db";

    // Initialize database (Create table if not exists)
    public static void initializeDB() {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS expenses (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "amount DOUBLE, " +
                    "category TEXT, " +
                    "date TEXT, " +  // Date is now added automatically
                    "time TEXT)";    // Time is now added automatically
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}
