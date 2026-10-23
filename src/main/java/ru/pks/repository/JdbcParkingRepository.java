package ru.pks.repository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import ru.pks.config.Database;
import ru.pks.exception.DataAccessException;
import ru.pks.model.Booking;
import ru.pks.model.BookingStatus;
import ru.pks.model.Employee;

// настоящая реализация ParkingRepository, ходит в postgres через обычный jdbc
// каждый метод работает по одной схеме:
// открыли подключение -> подготовили sql -> выполнили -> перегнали строки в java-объекты
public final class JdbcParkingRepository implements ParkingRepository {
    private final Database database;

    public JdbcParkingRepository(Database database) {
        this.database = database;
    }

    @Override
    public List<Employee> employees() {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("SELECT * FROM employees ORDER BY id");
             var resultSet = statement.executeQuery()) {
            List<Employee> employees = new ArrayList<>();
            while (resultSet.next()) {
                employees.add(new Employee(resultSet.getLong("id"), resultSet.getString("name"), resultSet.getString("email")));
            }
            return employees;
        } catch (SQLException e) { throw failure(e); }
    }

    @Override
    public Employee addEmployee(String name, String email) {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("INSERT INTO employees(name, email) VALUES (?, ?) RETURNING id")) {
            statement.setString(1, name);
            statement.setString(2, email);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return new Employee(resultSet.getLong(1), name, email);
            }
        } catch (SQLException e) { throw failure(e); }
    }

    @Override
    public List<Booking> bookings() {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("SELECT * FROM bookings ORDER BY id");
             var resultSet = statement.executeQuery()) {
            List<Booking> bookings = new ArrayList<>();
            while (resultSet.next()) {
                // статус в базе хранится строкой типа "CREATED", valueOf превращает её обратно в enum
                bookings.add(new Booking(resultSet.getLong("id"), resultSet.getLong("employee_id"),
                    resultSet.getInt("spot"), resultSet.getObject("booking_date", LocalDate.class),
                    BookingStatus.valueOf(resultSet.getString("status"))));
            }
            return bookings;
        } catch (SQLException e) { throw failure(e); }
    }

    @Override
    public Booking save(Booking booking) {
        // если id = 0, значит брони ещё нет, делаем INSERT, иначе UPDATE по id
        boolean isNewBooking = booking.id() == 0;
        String sql = isNewBooking
            ? "INSERT INTO bookings(employee_id, spot, booking_date, status) VALUES (?, ?, ?, ?) RETURNING id"
            : "UPDATE bookings SET employee_id=?, spot=?, booking_date=?, status=? WHERE id=? RETURNING id";
        try (var connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, booking.employeeId());
            statement.setInt(2, booking.spot());
            statement.setObject(3, booking.date());
            statement.setString(4, booking.status().name());
            // пятый ? есть только в UPDATE (WHERE id=?)
            if (!isNewBooking) statement.setLong(5, booking.id());
            try (var resultSet = statement.executeQuery()) {
                // если UPDATE не нашёл строку с таким id, RETURNING ничего не вернёт
                if (!resultSet.next()) throw new IllegalArgumentException("Бронирование не найдено.");
                return new Booking(resultSet.getLong(1), booking.employeeId(), booking.spot(), booking.date(), booking.status());
            }
        } catch (SQLException e) { throw failure(e); }
    }

    @Override
    public boolean delete(long id) {
        try (var connection = database.connect(); var statement = connection.prepareStatement("DELETE FROM bookings WHERE id=?")) {
            statement.setLong(1, id);
            int deletedRowsCount = statement.executeUpdate();
            return deletedRowsCount > 0;
        } catch (SQLException e) { throw failure(e); }
    }

    @Override
    public List<String> tables() {
        // pg_tables это системная табличка постгреса со списком всех таблиц, берём только наши из схемы public
        try (var connection = database.connect();
             var statement = connection.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename");
             var resultSet = statement.executeQuery()) {
            List<String> tableNames = new ArrayList<>();
            while (resultSet.next()) tableNames.add(resultSet.getString(1));
            return tableNames;
        } catch (SQLException e) { throw failure(e); }
    }

    // переводим ошибку постгреса на человеческий
    // у каждой ошибки есть код SQLState, например 23505 это нарушение уникальности
    // (тот же email или уникальные индексы на место/сотрудника в день из sql/schema.sql)
    private DataAccessException failure(SQLException e) {
        String sqlState = e.getSQLState() == null ? "" : e.getSQLState();
        String message = switch (sqlState) {
            case "23505" -> "Такая почта уже есть, место занято или у сотрудника уже есть бронь на эту дату.";
            case "23503" -> "Сотрудник не найден или используется в бронированиях.";
            case "23514", "23502" -> "Данные нарушают ограничения базы.";
            default -> "Ошибка PostgreSQL (" + e.getSQLState() + "). Проверьте подключение и схему базы.";
        };
        return new DataAccessException(message, e);
    }
}
