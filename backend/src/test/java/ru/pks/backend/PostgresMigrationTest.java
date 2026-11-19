package ru.pks.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class PostgresMigrationTest {
    @Test void migratesLegacyDataAndKeepsDatabaseConstraints() throws Exception {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://127.0.0.1:5433/pksjava");
        String user = System.getenv().getOrDefault("DB_USER", "pks_app");
        String password = System.getenv("DB_PASSWORD");
        String schema = "parking_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection control = DriverManager.getConnection(url, user, password)) {
            control.createStatement().execute("CREATE SCHEMA " + schema);
            try {
                String testUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
                try (Connection connection = DriverManager.getConnection(testUrl, user, password)) {
                    String original = new String(getClass().getResourceAsStream("/db/migration/V1__existing_parking.sql").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    connection.createStatement().execute(original);
                    connection.createStatement().execute("INSERT INTO employees(name,email) VALUES ('Legacy','legacy@company.ru'),('Second','second@company.ru')");
                    connection.createStatement().execute("INSERT INTO bookings(employee_id,spot,booking_date,status) VALUES (1,1,CURRENT_DATE,'CREATED')");
                    connection.createStatement().execute("CREATE TABLE entries (id BIGINT PRIMARY KEY)");
                    connection.createStatement().execute("INSERT INTO entries VALUES (123)");
                }
                Flyway.configure().dataSource(testUrl, user, password).schemas(schema).defaultSchema(schema)
                        .baselineOnMigrate(true).baselineVersion("0").load().migrate();
                try (Connection connection = DriverManager.getConnection(testUrl, user, password); Statement sql = connection.createStatement()) {
                    try (ResultSet row = sql.executeQuery("SELECT id,uuid,code,version FROM bookings")) {
                        assertTrue(row.next());
                        assertEquals(1, row.getLong("id"));
                        assertNotNull(row.getObject("uuid"));
                        assertEquals("B-1", row.getString("code"));
                        assertEquals(0, row.getLong("version"));
                    }
                    assertEquals("23505", assertThrows(SQLException.class, () -> sql.execute("INSERT INTO bookings(employee_id,spot,booking_date,status) VALUES (2,1,CURRENT_DATE,'CREATED')")).getSQLState());
                    sql.execute("UPDATE bookings SET archived=TRUE WHERE id=1");
                    sql.execute("INSERT INTO bookings(employee_id,spot,booking_date,status) VALUES (2,1,CURRENT_DATE,'CREATED')");
                    assertEquals("23505", assertThrows(SQLException.class, () -> sql.execute("UPDATE bookings SET archived=FALSE WHERE id=1")).getSQLState());
                    sql.execute("UPDATE bookings SET spot=5 WHERE id=1");
                    try (ResultSet row = sql.executeQuery("SELECT version FROM bookings WHERE id=1")) {
                        row.next();
                        assertEquals(2, row.getLong(1));
                    }
                    try (ResultSet row = sql.executeQuery("SELECT id FROM entries")) { row.next(); assertEquals(123, row.getLong(1)); }
                    assertEquals("23514", assertThrows(SQLException.class, () -> sql.execute("UPDATE bookings SET spot=51 WHERE id=1")).getSQLState());
                }
            } finally {
                // имя схемы создается здесь из uuid и не поступает от пользователя
                control.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
