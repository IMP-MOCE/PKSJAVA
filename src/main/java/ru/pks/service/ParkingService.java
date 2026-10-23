package ru.pks.service;

import ru.pks.model.*;
import ru.pks.repository.ParkingRepository;
import java.time.LocalDate;
import java.util.*;

// тут вся логика парковки: проверки, поиск, фильтры, статистика
// и консольное меню, и окно на javafx ходят только сюда, напрямую в репозиторий никто не лезет
// ошибки ввода кидаем как IllegalArgumentException с текстом для пользователя, интерфейс просто показывает этот текст
public final class ParkingService {
    private final ParkingRepository repository;

    // репозиторий передаётся снаружи, так в тестах можно подсунуть хранилище в памяти вместо postgres
    public ParkingService(ParkingRepository repository) {
        this.repository = repository;
    }

    // просто пробрасываем в репозиторий без доп логики
    public List<Employee> employees() { return repository.employees(); }
    public List<Booking> bookings() { return repository.bookings(); }
    public List<String> tables() { return repository.tables(); }

    // добавить сотрудника: проверяем имя и почту, приводим почту к нижнему регистру, проверяем что такой ещё нет
    public Employee addEmployee(String name, String email) {
        if (name == null || name.isBlank() || name.strip().length() > 100)
            throw new IllegalArgumentException("Имя должно содержать от 1 до 100 символов.");
        // регулярка грубо проверяет вид почты: что-то@что-то.что-то и без пробелов
        if (email == null || email.length() > 100 || !email.strip().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Укажите корректную почту, не более 100 символов.");
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        // stream api: идём по всем сотрудникам и смотрим, есть ли хоть один (anyMatch) с такой же почтой
        if (employees().stream().anyMatch(employee -> employee.email().equalsIgnoreCase(normalizedEmail)))
            throw new IllegalArgumentException("Сотрудник с такой почтой уже есть.");
        return repository.addEmployee(name.strip(), normalizedEmail);
    }

    // найти бронь по id, если её нет, кидаем ошибку
    public Booking get(long id) {
        if (id <= 0) throw new IllegalArgumentException("ID должен быть положительным.");
        // stream api: берём все брони, оставляем ту, у которой нужный id, и достаём первую
        // findFirst отдаёт Optional (коробку, которая может быть пустой), orElseThrow кидает ошибку если пусто
        return bookings().stream().filter(booking -> booking.id() == id).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Бронирование не найдено: " + id));
    }

    // новая бронь всегда стартует в статусе CREATED, id = 0, настоящий выдаст база
    public Booking create(long employeeId, int spot, LocalDate date) {
        return save(new Booking(0, employeeId, spot, date, BookingStatus.CREATED));
    }

    // поменять место и дату у брони, сотрудник и статус остаются прежними
    // закрытые брони (завершённые и отменённые) трогать нельзя
    public Booking update(long id, int spot, LocalDate date) {
        Booking currentBooking = get(id);
        if (!currentBooking.status().active()) throw new IllegalArgumentException("Закрытое бронирование нельзя менять.");
        return save(new Booking(id, currentBooking.employeeId(), spot, date, currentBooking.status()));
    }

    // сменить статус брони с проверкой, что такой переход вообще разрешён (правила в BookingStatus.canChangeTo)
    public Booking changeStatus(long id, BookingStatus newStatus) {
        Booking currentBooking = get(id);
        if (newStatus == null || !currentBooking.status().canChangeTo(newStatus))
            throw new IllegalArgumentException("Недопустимый переход статуса.");
        if (newStatus == BookingStatus.COMPLETED && currentBooking.date().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Нельзя завершить будущее бронирование.");
        // если статус не поменялся, сохранять нечего
        if (newStatus == currentBooking.status()) return currentBooking;
        Booking updatedBooking = new Booking(id, currentBooking.employeeId(), currentBooking.spot(), currentBooking.date(), newStatus);
        // если бронь остаётся активной, гоняем её через все проверки в save (дата не в прошлом, место свободно)
        // а если закрываем (отмена или завершение), пишем сразу в репозиторий без проверок,
        // иначе просроченную бронь нельзя было бы отменить из-за проверки "дата в прошлом"
        return newStatus.active() ? save(updatedBooking) : repository.save(updatedBooking);
    }

    // общие проверки перед сохранением новой или изменённой брони
    private Booking save(Booking booking) {
        // noneMatch = ни один сотрудник не подошёл, значит такого сотрудника нет
        if (employees().stream().noneMatch(employee -> employee.id() == booking.employeeId()))
            throw new IllegalArgumentException("Сотрудник не найден.");
        if (booking.spot() < 1 || booking.spot() > 50)
            throw new IllegalArgumentException("Номер места должен быть от 1 до 50.");
        if (booking.date() == null || booking.date().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Дата бронирования не может быть в прошлом.");
        // проверяем конфликты с другими бронями на тот же день
        for (Booking otherBooking : bookings()) {
            // пропускаем саму себя (при изменении), закрытые брони и брони на другие даты, они не мешают
            boolean isSameBooking = otherBooking.id() == booking.id();
            boolean isOnAnotherDate = !otherBooking.date().equals(booking.date());
            if (isSameBooking || !otherBooking.status().active() || isOnAnotherDate) continue;
            if (otherBooking.spot() == booking.spot()) throw new IllegalArgumentException("Место на эту дату уже занято.");
            if (otherBooking.employeeId() == booking.employeeId())
                throw new IllegalArgumentException("Сотрудник уже забронировал место на эту дату.");
        }
        return repository.save(booking);
    }

    // удалить бронь, get(id) тут нужен только чтобы кинуть понятную ошибку, если брони нет
    public void delete(long id) {
        get(id);
        if (!repository.delete(id)) throw new IllegalArgumentException("Бронирование не найдено.");
    }

    // брони сотрудников, у которых в фио есть кусок nameFragment (без учёта регистра)
    public List<Booking> searchEmployee(String nameFragment) {
        String lowerCaseFragment = nameFragment.strip().toLowerCase(Locale.ROOT);
        // шаг 1: собираем id всех подходящих сотрудников в множество
        Set<Long> matchingEmployeeIds = new HashSet<>();
        employees().stream().filter(employee -> employee.name().toLowerCase(Locale.ROOT).contains(lowerCaseFragment))
            .forEach(employee -> matchingEmployeeIds.add(employee.id()));
        // шаг 2: оставляем только брони этих сотрудников
        return bookings().stream().filter(booking -> matchingEmployeeIds.contains(booking.employeeId())).toList();
    }

    // все брони на конкретное место
    public List<Booking> searchSpot(int spot) {
        return bookings().stream().filter(booking -> booking.spot() == spot).toList();
    }

    // главный поиск для окна: текст + статус + период, всё одновременно
    // любой параметр можно не задавать (пустая строка или null), тогда этот фильтр просто не работает
    // текст ищется в фио и почте, а если там написано число, то ещё и по точному номеру места
    // границы периода включаются: from и to тоже попадают в результат
    public List<Booking> search(String query, BookingStatus status, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("Начало периода позже конца.");
        String lowerCaseQuery = query.strip().toLowerCase(Locale.ROOT);
        // сначала ищем id сотрудников, у которых в "фио почта" встречается запрос
        Set<Long> matchingEmployeeIds = new HashSet<>();
        employees().stream()
            .filter(employee -> (employee.name() + " " + employee.email()).toLowerCase(Locale.ROOT).contains(lowerCaseQuery))
            .forEach(employee -> matchingEmployeeIds.add(employee.id()));
        // потом прогоняем все брони через фильтры по очереди, бронь должна пройти каждый
        return bookings().stream()
            .filter(booking -> lowerCaseQuery.isEmpty()
                || matchingEmployeeIds.contains(booking.employeeId())
                || Integer.toString(booking.spot()).equals(lowerCaseQuery))
            .filter(booking -> status == null || booking.status() == status)
            .filter(booking -> from == null || !booking.date().isBefore(from))
            .filter(booking -> to == null || !booking.date().isAfter(to)).toList();
    }

    // все брони с указанным статусом
    public List<Booking> filterStatus(BookingStatus status) {
        return bookings().stream().filter(booking -> booking.status() == status).toList();
    }

    // все брони с датой от from до to включительно
    public List<Booking> filterDates(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("Начало периода позже конца.");
        return bookings().stream().filter(booking -> !booking.date().isBefore(from) && !booking.date().isAfter(to)).toList();
    }

    // все брони, отсортированные по дате (byDate = true) или по номеру места (false)
    // если значения совпали, дальше сортируем по id, чтобы порядок всегда был одинаковый
    public List<Booking> sorted(boolean byDate) {
        Comparator<Booking> sortOrder = byDate ? Comparator.comparing(Booking::date) : Comparator.comparingInt(Booking::spot);
        return bookings().stream().sorted(sortOrder.thenComparingLong(Booking::id)).toList();
    }

    // статистика по всей базе в виде "название -> число"
    // LinkedHashMap запоминает порядок добавления, поэтому строчки выводятся ровно в этом порядке
    // всего 7 строк: сотрудники, брони, по одной на каждый из 4 статусов и сколько мест занято сегодня
    public Map<String, Long> statistics() {
        List<Booking> allBookings = bookings();
        Map<String, Long> statisticsByLabel = new LinkedHashMap<>();
        statisticsByLabel.put("Сотрудников", (long) employees().size());
        statisticsByLabel.put("Бронирований", (long) allBookings.size());
        // для статусов ключом идёт имя enum (CREATED и т.д.), окно потом переводит их на русский
        for (BookingStatus status : BookingStatus.values())
            statisticsByLabel.put(status.name(), allBookings.stream().filter(booking -> booking.status() == status).count());
        statisticsByLabel.put("Занято мест сегодня", allBookings.stream()
            .filter(booking -> booking.status().active() && booking.date().equals(LocalDate.now())).count());
        return statisticsByLabel;
    }
}
