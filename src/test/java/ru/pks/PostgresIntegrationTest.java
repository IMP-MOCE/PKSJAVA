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

// тест с настоящим postgres из докера
// запускается только если RUN_DB_TESTS=true, это выставляет `run.cmd test`, иначе junit его пропускает
// чтобы не испортить рабочие данные, создаём отдельную временную схему (как папку внутри бд), гоняем всё в ней и в конце удаляем
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class PostgresIntegrationTest {
    // junit сам создаёт временную папку под файлы теста и потом её чистит
    @TempDir Path tempFolder;

    @Test void postgresCrudConstraintsSeedAndExcel() throws Exception {
        Database mainDatabase = Database.fromEnvironment();
        // случайное имя, чтобы схемы разных запусков не пересекались
        String testSchemaName = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = mainDatabase.connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + testSchemaName);
        }
        // тот же адрес бд, но с параметром currentSchema, тогда все запросы пойдут в нашу временную схему
        String baseUrl = System.getenv("DB_URL");
        String testSchemaUrl = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + testSchemaName;
        Database testDatabase = new Database(testSchemaUrl, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
        try {
            // создаём таблицы и дважды заливаем примеры, второй раз ничего не должен задублировать
            try (var connection = testDatabase.connect(); var statement = connection.createStatement()) {
                statement.execute(Files.readString(Path.of("sql/schema.sql")));
                statement.execute(Files.readString(Path.of("sql/seed.sql")));
                statement.execute(Files.readString(Path.of("sql/seed.sql")));
            }
            var repository = new JdbcParkingRepository(testDatabase);
            var service = new ParkingService(repository);
            assertEquals(5, repository.employees().size());
            assertEquals(10, repository.bookings().size());
            // в примерах встречаются все 4 статуса
            assertEquals(4, repository.bookings().stream().map(Booking::status).distinct().count());

            // имя с sql-инъекцией должно сохраниться как обычный текст, а таблица bookings остаться жива
            var testEmployee = service.addEmployee("Тест '); DROP TABLE bookings; --", "test@company.ru");
            var bookingDate = LocalDate.now().plusDays(50);
            var testBooking = service.create(testEmployee.id(), 50, bookingDate);
            // новый репозиторий = чтение заново из базы, значит бронь реально записалась
            assertTrue(new JdbcParkingRepository(testDatabase).bookings().contains(testBooking));

            // тут пишем мимо сервиса прямо в репозиторий, чтобы проверить, что сама база тоже не пустит кривые данные
            // место 50 на эту дату уже занято
            assertThrows(DataAccessException.class, () -> repository.save(new Booking(0, 1, 50, bookingDate, BookingStatus.CREATED)));
            // у сотрудника уже есть бронь на эту дату
            assertThrows(DataAccessException.class, () -> repository.save(new Booking(0, testEmployee.id(), 49, bookingDate, BookingStatus.CREATED)));
            // нет такого сотрудника
            assertThrows(DataAccessException.class, () -> repository.save(new Booking(0, 99999, 49, bookingDate, BookingStatus.CREATED)));
            // почта уже занята
            assertThrows(DataAccessException.class, () -> repository.addEmployee("Дубликат", "test@company.ru"));

            assertEquals(49, service.update(testBooking.id(), 49, bookingDate).spot());
            // отменили и на то же место в тот же день создали новую, отменённая не мешает
            service.changeStatus(testBooking.id(), BookingStatus.CANCELLED);
            var replacementBooking = service.create(testEmployee.id(), 49, bookingDate);

            // проверяем excel: 2 листа, строки = заголовок + записи (6 сотрудников, 12 броней)
            Path excelFile = tempFolder.resolve("parking.xlsx");
            ExcelExporter.export(excelFile, service.employees(), service.bookings());
            try (var workbook = new XSSFWorkbook(Files.newInputStream(excelFile))) {
                assertEquals(2, workbook.getNumberOfSheets());
                assertEquals(7, workbook.getSheet("Сотрудники").getPhysicalNumberOfRows());
                assertEquals(13, workbook.getSheet("Бронирования").getPhysicalNumberOfRows());
                assertEquals(testEmployee.name(), workbook.getSheet("Сотрудники").getRow(6).getCell(1).getStringCellValue());
            }
            // убираем за собой, повторное удаление вернёт false
            service.delete(testBooking.id());
            service.delete(replacementBooking.id());
            assertFalse(repository.delete(testBooking.id()));
            assertEquals(10, repository.bookings().size());

            // дальше проверяем уже собранный jar, путь к нему передаёт project.ps1 через -DpackagedJar
            if (System.getProperty("packagedJar") != null) {
                // через консольное меню: пункт 5, сотрудник 5, место 24, сегодня, выход
                runJar(testSchemaUrl, "5\n5\n24\n" + LocalDate.now() + "\n0\n");
                var bookingFromJar = repository.bookings().stream().filter(booking -> booking.spot() == 24).findFirst().orElseThrow();
                assertEquals(5, bookingFromJar.employeeId());
                // пункт 6: переносим её на место 25
                runJar(testSchemaUrl, "6\n" + bookingFromJar.id() + "\n25\n" + LocalDate.now() + "\n0\n");
                assertEquals(25, service.get(bookingFromJar.id()).spot());
                // пункт 8: удаляем
                runJar(testSchemaUrl, "8\n" + bookingFromJar.id() + "\n0\n");
                assertEquals(10, repository.bookings().size());
                // и проверка графической версии из jar, подробности в PackagedFxSmoke
                if ("true".equals(System.getenv("RUN_FX_TESTS"))) {
                    String classpath = System.getProperty("packagedJar") + java.io.File.pathSeparator
                        + Path.of("target/test-classes").toAbsolutePath();
                    var processBuilder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                        "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp", classpath, "ru.pks.ui.PackagedFxSmoke");
                    processBuilder.environment().put("DB_URL", testSchemaUrl);
                    Path logFile = tempFolder.resolve("javafx.log");
                    var process = processBuilder.redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
                    try {
                        assertTrue(process.waitFor(40, TimeUnit.SECONDS), "JavaFX JAR завис при запуске");
                        assertEquals(0, process.exitValue(), Files.readString(logFile));
                    } finally { if (process.isAlive()) process.destroyForcibly(); }
                }
            }
        } finally {
            // что бы ни случилось, сносим временную схему вместе со всем содержимым
            try (var connection = mainDatabase.connect(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + testSchemaName + " CASCADE");
            }
        }
    }

    // запускает готовый jar в консольном режиме отдельным процессом и скармливает ему typedCommands как ввод с клавиатуры
    // проверяет, что прога завершилась сама, с кодом 0 и ни разу не написала "Ошибка:"
    private void runJar(String databaseUrl, String typedCommands) throws Exception {
        Path logFile = tempFolder.resolve("console.log");
        var processBuilder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
            "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", System.getProperty("packagedJar"), "--console");
        processBuilder.environment().put("DB_URL", databaseUrl);
        processBuilder.redirectErrorStream(true).redirectOutput(logFile.toFile());
        Process process = processBuilder.start();
        try {
            try (var processInput = process.getOutputStream()) { processInput.write(typedCommands.getBytes(StandardCharsets.UTF_8)); }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "JAR не завершился за 30 секунд");
            String consoleOutput = Files.readString(logFile);
            assertEquals(0, process.exitValue(), consoleOutput);
            assertFalse(consoleOutput.contains("Ошибка:"), consoleOutput);
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
