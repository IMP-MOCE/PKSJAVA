package ru.pks.repository;

import ru.pks.config.Database;
import ru.pks.exception.DataAccessException;
import ru.pks.model.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class JdbcParkingRepository implements ParkingRepository {
    private final Database database;

    public JdbcParkingRepository(Database database) {
        this.database = database;
    }

    public List<Employee> employees() {
        try (var c = database.connect();
             var s = c.prepareStatement("SELECT * FROM employees ORDER BY id");
             var r = s.executeQuery()) {
            List<Employee> result = new ArrayList<>();
            while (r.next()) result.add(new Employee(r.getLong("id"), r.getString("name"), r.getString("email")));
            return result;
        } catch (SQLException e) { throw failure(e); }
    }

    public Employee addEmployee(String name, String email) {
        try (var c = database.connect();
             var s = c.prepareStatement("INSERT INTO employees(name, email) VALUES (?, ?) RETURNING id")) {
            s.setString(1, name);
            s.setString(2, email);
            try (var r = s.executeQuery()) {
                r.next();
                return new Employee(r.getLong(1), name, email);
            }
        } catch (SQLException e) { throw failure(e); }
    }

    public List<Booking> bookings() {
        try (var c = database.connect();
             var s = c.prepareStatement("SELECT * FROM bookings ORDER BY id");
             var r = s.executeQuery()) {
            List<Booking> result = new ArrayList<>();
            while (r.next()) result.add(new Booking(r.getLong("id"), r.getLong("employee_id"),
                r.getInt("spot"), r.getObject("booking_date", LocalDate.class),
                BookingStatus.valueOf(r.getString("status"))));
            return result;
        } catch (SQLException e) { throw failure(e); }
    }

    public Booking save(Booking b) {
        String sql = b.id() == 0
            ? "INSERT INTO bookings(employee_id, spot, booking_date, status) VALUES (?, ?, ?, ?) RETURNING id"
            : "UPDATE bookings SET employee_id=?, spot=?, booking_date=?, status=? WHERE id=? RETURNING id";
        try (var c = database.connect(); var s = c.prepareStatement(sql)) {
            s.setLong(1, b.employeeId());
            s.setInt(2, b.spot());
            s.setObject(3, b.date());
            s.setString(4, b.status().name());
            if (b.id() != 0) s.setLong(5, b.id());
            try (var r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Бронирование не найдено.");
                return new Booking(r.getLong(1), b.employeeId(), b.spot(), b.date(), b.status());
            }
        } catch (SQLException e) { throw failure(e); }
    }

    public boolean delete(long id) {
        try (var c = database.connect(); var s = c.prepareStatement("DELETE FROM bookings WHERE id=?")) {
            s.setLong(1, id);
            return s.executeUpdate() > 0;
        } catch (SQLException e) { throw failure(e); }
    }

    public List<String> tables() {
        try (var c = database.connect();
             var s = c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename");
             var r = s.executeQuery()) {
            List<String> result = new ArrayList<>();
            while (r.next()) result.add(r.getString(1));
            return result;
        } catch (SQLException e) { throw failure(e); }
    }

    private DataAccessException failure(SQLException e) {
        String message = switch (e.getSQLState() == null ? "" : e.getSQLState()) {
            case "23505" -> "Такая почта уже есть, место занято или у сотрудника уже есть бронь на эту дату.";
            case "23503" -> "Сотрудник не найден или используется в бронированиях.";
            case "23514", "23502" -> "Данные нарушают ограничения базы.";
            default -> "Ошибка PostgreSQL (" + e.getSQLState() + "). Проверьте подключение и схему базы.";
        };
        return new DataAccessException(message, e);
    }
}
