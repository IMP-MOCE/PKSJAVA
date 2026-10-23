package ru.pks.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// тут просто лежат адрес бд, логин и пароль, и по запросу класс открывает новое подключение
// сам по себе он ничего не читает и не пишет, этим занимается репозиторий
public final class Database {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public Database(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    // берём настройки из переменных окружения, их выставляет project.ps1 перед запуском
    // пароль обязателен, а для адреса и логина есть значения по умолчанию под наш докер
    public static Database fromEnvironment() {
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Не задан DB_PASSWORD. Запустите приложение через run.cmd.");
        }
        return new Database(
            System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://127.0.0.1:5433/pksjava"),
            System.getenv().getOrDefault("DB_USER", "pks_app"), password);
    }

    // каждый раз отдаёт новое подключение, закрывать его должен тот, кто взял (у нас это try-with-resources)
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}
