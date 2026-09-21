package ru.pks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.pks.config.Database;
import ru.pks.exception.DataAccessException;
import ru.pks.model.*;
import ru.pks.repository.JdbcParkingRepository;
import ru.pks.service.ParkingService;
import ru.pks.util.ExcelExporter;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class PostgresIntegrationTest {
    @TempDir Path temp;

    @Test void postgresCrudConstraintsSeedAndExcel() throws Exception {
        Database main = Database.fromEnvironment();
        String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = main.connect(); var s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
        }
        String url = System.getenv("DB_URL");
        Database test = new Database(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
            System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
        try {
            try (var c = test.connect(); var s = c.createStatement()) {
                s.execute(Files.readString(Path.of("sql/schema.sql")));
                s.execute(Files.readString(Path.of("sql/seed.sql")));
                s.execute(Files.readString(Path.of("sql/seed.sql")));
            }
            var repo = new JdbcParkingRepository(test);
            var service = new ParkingService(repo);
            assertEquals(5, repo.employees().size());
            assertEquals(10, repo.bookings().size());
            assertEquals(4, repo.bookings().stream().map(Booking::status).distinct().count());
            var employee = service.addEmployee("Тест '); DROP TABLE bookings; --", "test@company.ru");
            var date = LocalDate.now().plusDays(50);
            var b = service.create(employee.id(), 50, date);
            assertTrue(new JdbcParkingRepository(test).bookings().contains(b));
            assertThrows(DataAccessException.class, () -> repo.save(new Booking(0, 1, 50, date, BookingStatus.CREATED)));
            assertThrows(DataAccessException.class, () -> repo.save(new Booking(0, employee.id(), 49, date, BookingStatus.CREATED)));
            assertThrows(DataAccessException.class, () -> repo.save(new Booking(0, 99999, 49, date, BookingStatus.CREATED)));
            assertThrows(DataAccessException.class, () -> repo.addEmployee("Дубликат", "test@company.ru"));
            assertEquals(49, service.update(b.id(), 49, date).spot());
            service.changeStatus(b.id(), BookingStatus.CANCELLED);
            var replacement = service.create(employee.id(), 49, date);
            Path file = temp.resolve("parking.xlsx");
            ExcelExporter.export(file, service.employees(), service.bookings());
            try (var book = new XSSFWorkbook(Files.newInputStream(file))) {
                assertEquals(2, book.getNumberOfSheets());
                assertEquals(7, book.getSheet("Сотрудники").getPhysicalNumberOfRows());
                assertEquals(13, book.getSheet("Бронирования").getPhysicalNumberOfRows());
                assertEquals(employee.name(), book.getSheet("Сотрудники").getRow(6).getCell(1).getStringCellValue());
            }
            service.delete(b.id());
            service.delete(replacement.id());
            assertFalse(repo.delete(b.id()));
            assertEquals(10, repo.bookings().size());
            if (System.getProperty("packagedJar") != null) {
                String testUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
                runJar(testUrl, "5\n5\n24\n" + LocalDate.now() + "\n0\n");
                var created = repo.bookings().stream().filter(item -> item.spot() == 24).findFirst().orElseThrow();
                assertEquals(5, created.employeeId());
                runJar(testUrl, "6\n" + created.id() + "\n25\n" + LocalDate.now() + "\n0\n");
                assertEquals(25, service.get(created.id()).spot());
                runJar(testUrl, "8\n" + created.id() + "\n0\n");
                assertEquals(10, repo.bookings().size());
            }
        } finally {
            try (var c = main.connect(); var s = c.createStatement()) {
                s.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private void runJar(String url, String commands) throws Exception {
        Path log = temp.resolve("console.log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
            "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", System.getProperty("packagedJar"));
        builder.environment().put("DB_URL", url);
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process process = builder.start();
        try {
            try (var input = process.getOutputStream()) { input.write(commands.getBytes(StandardCharsets.UTF_8)); }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "JAR не завершился за 30 секунд");
            String output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            assertFalse(output.contains("Ошибка:"), output);
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
