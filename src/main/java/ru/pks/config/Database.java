package ru.pks.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static Database fromEnvironment() {
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Не задан DB_PASSWORD. Запустите приложение через run.cmd.");
        }
        return new Database(
            System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://127.0.0.1:5433/pksjava"),
            System.getenv().getOrDefault("DB_USER", "pks_app"), password);
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
