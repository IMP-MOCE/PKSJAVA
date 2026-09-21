package ru.pks.service;

import ru.pks.model.*;
import ru.pks.repository.ParkingRepository;
import java.time.LocalDate;
import java.util.*;

public final class ParkingService {
    private final ParkingRepository repository;

    public ParkingService(ParkingRepository repository) {
        this.repository = repository;
    }

    public List<Employee> employees() { return repository.employees(); }
    public List<Booking> bookings() { return repository.bookings(); }
    public List<String> tables() { return repository.tables(); }

    public Employee addEmployee(String name, String email) {
        if (name == null || name.isBlank() || name.strip().length() > 100)
            throw new IllegalArgumentException("Имя должно содержать от 1 до 100 символов.");
        if (email == null || email.length() > 100 || !email.strip().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Укажите корректную почту, не более 100 символов.");
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (employees().stream().anyMatch(e -> e.email().equalsIgnoreCase(normalized)))
            throw new IllegalArgumentException("Сотрудник с такой почтой уже есть.");
        return repository.addEmployee(name.strip(), normalized);
    }

    public Booking get(long id) {
        if (id <= 0) throw new IllegalArgumentException("ID должен быть положительным.");
        return bookings().stream().filter(b -> b.id() == id).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Бронирование не найдено: " + id));
    }

    public Booking create(long employeeId, int spot, LocalDate date) {
        return save(new Booking(0, employeeId, spot, date, BookingStatus.CREATED));
    }

    public Booking update(long id, int spot, LocalDate date) {
        Booking old = get(id);
        if (!old.status().active()) throw new IllegalArgumentException("Закрытое бронирование нельзя менять.");
        return save(new Booking(id, old.employeeId(), spot, date, old.status()));
    }

    public Booking changeStatus(long id, BookingStatus status) {
        Booking old = get(id);
        if (status == null || !old.status().canChangeTo(status))
            throw new IllegalArgumentException("Недопустимый переход статуса.");
        if (status == BookingStatus.COMPLETED && old.date().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Нельзя завершить будущее бронирование.");
        // Отменить просроченную бронь разрешено, занять место в прошлом — нет.
        if (status == old.status()) return old;
        Booking next = new Booking(id, old.employeeId(), old.spot(), old.date(), status);
        return status.active() ? save(next) : repository.save(next);
    }

    private Booking save(Booking b) {
        if (employees().stream().noneMatch(e -> e.id() == b.employeeId()))
            throw new IllegalArgumentException("Сотрудник не найден.");
        if (b.spot() < 1 || b.spot() > 50)
            throw new IllegalArgumentException("Номер места должен быть от 1 до 50.");
        if (b.date() == null || b.date().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Дата бронирования не может быть в прошлом.");
        for (Booking other : bookings()) {
            if (other.id() == b.id() || !other.status().active() || !other.date().equals(b.date())) continue;
            if (other.spot() == b.spot()) throw new IllegalArgumentException("Место на эту дату уже занято.");
            if (other.employeeId() == b.employeeId())
                throw new IllegalArgumentException("Сотрудник уже забронировал место на эту дату.");
        }
        return repository.save(b);
    }

    public void delete(long id) {
        get(id);
        if (!repository.delete(id)) throw new IllegalArgumentException("Бронирование не найдено.");
    }

    public List<Booking> searchEmployee(String name) {
        String query = name.strip().toLowerCase(Locale.ROOT);
        Set<Long> ids = new HashSet<>();
        employees().stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(query))
            .forEach(e -> ids.add(e.id()));
        return bookings().stream().filter(b -> ids.contains(b.employeeId())).toList();
    }

    public List<Booking> searchSpot(int spot) {
        return bookings().stream().filter(b -> b.spot() == spot).toList();
    }

    public List<Booking> filterStatus(BookingStatus status) {
        return bookings().stream().filter(b -> b.status() == status).toList();
    }

    public List<Booking> filterDates(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("Начало периода позже конца.");
        return bookings().stream().filter(b -> !b.date().isBefore(from) && !b.date().isAfter(to)).toList();
    }

    public List<Booking> sorted(boolean byDate) {
        Comparator<Booking> order = byDate ? Comparator.comparing(Booking::date) : Comparator.comparingInt(Booking::spot);
        return bookings().stream().sorted(order.thenComparingLong(Booking::id)).toList();
    }

    public Map<String, Long> statistics() {
        List<Booking> all = bookings();
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("Сотрудников", (long) employees().size());
        result.put("Бронирований", (long) all.size());
        for (BookingStatus status : BookingStatus.values())
            result.put(status.name(), all.stream().filter(b -> b.status() == status).count());
        result.put("Занято мест сегодня", all.stream()
            .filter(b -> b.status().active() && b.date().equals(LocalDate.now())).count());
        return result;
    }
}
