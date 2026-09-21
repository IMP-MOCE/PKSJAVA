package ru.pks.ui;

import ru.pks.exception.DataAccessException;
import ru.pks.model.*;
import ru.pks.service.ParkingService;
import ru.pks.util.ExcelExporter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class ConsoleMenu {
    private final ParkingService service;
    private final Scanner input;

    public ConsoleMenu(ParkingService service, Scanner input) {
        this.service = service;
        this.input = input;
    }

    public void run() {
        while (true) {
            System.out.println("""

                КОРПОРАТИВНАЯ ПАРКОВКА
                1. Сотрудники       2. Добавить сотрудника
                3. Все брони        4. Бронь по ID
                5. Забронировать    6. Изменить место/дату
                7. Изменить статус  8. Удалить бронь
                9. Поиск по ФИО    10. Поиск по месту
                11. По статусу     12. По периоду
                13. По дате ↑      14. По месту ↑
                15. Статистика     16. Экспорт Excel
                17. Таблицы БД     0. Выход
                """);
            try {
                switch (read("Действие: ")) {
                    case "0" -> { return; }
                    case "1" -> service.employees().forEach(e ->
                        System.out.printf("%d | %s | %s%n", e.id(), e.name(), e.email()));
                    case "2" -> System.out.println("Добавлен: " + service.addEmployee(read("ФИО: "), read("Почта: ")));
                    case "3" -> display(service.bookings());
                    case "4" -> display(List.of(service.get(id())));
                    case "5" -> display(List.of(service.create(number("ID сотрудника: "), spot(), date("Дата"))));
                    case "6" -> display(List.of(service.update(id(), spot(), date("Новая дата"))));
                    case "7" -> display(List.of(service.changeStatus(id(), status())));
                    case "8" -> { service.delete(id()); System.out.println("Удалено."); }
                    case "9" -> display(service.searchEmployee(read("Фрагмент ФИО: ")));
                    case "10" -> display(service.searchSpot(spot()));
                    case "11" -> display(service.filterStatus(status()));
                    case "12" -> display(service.filterDates(date("С"), date("По")));
                    case "13" -> display(service.sorted(true));
                    case "14" -> display(service.sorted(false));
                    case "15" -> service.statistics().forEach((label, value) -> System.out.println(label + ": " + value));
                    case "16" -> {
                        Path file = Path.of("export", "parking.xlsx");
                        ExcelExporter.export(file, service.employees(), service.bookings());
                        System.out.println("Сохранено: " + file.toAbsolutePath());
                    }
                    case "17" -> {
                        service.tables().forEach(System.out::println);
                        System.out.println("Содержимое employees:");
                        service.employees().forEach(System.out::println);
                        System.out.println("Содержимое bookings:");
                        display(service.bookings());
                    }
                    default -> System.out.println("Выберите пункт от 0 до 17.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите целое число допустимого размера.");
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: дата должна быть в формате ГГГГ-ММ-ДД.");
            } catch (IllegalArgumentException | DataAccessException e) {
                System.out.println("Ошибка: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("Не удалось сохранить Excel: " + e.getMessage());
            } catch (NoSuchElementException e) {
                return;
            }
        }
    }

    private String read(String prompt) {
        System.out.print(prompt);
        return input.nextLine().strip();
    }

    private long number(String prompt) { return Long.parseLong(read(prompt)); }
    private long id() { return number("ID бронирования: "); }
    private int spot() { return Integer.parseInt(read("Место (1–50): ")); }
    private LocalDate date(String label) { return LocalDate.parse(read(label + " (ГГГГ-ММ-ДД): ")); }

    private BookingStatus status() {
        String value = read("Статус (CREATED / CONFIRMED / COMPLETED / CANCELLED): ");
        try { return BookingStatus.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Неизвестный статус."); }
    }

    private void display(List<Booking> bookings) {
        Map<Long, String> names = new HashMap<>();
        service.employees().forEach(e -> names.put(e.id(), e.name()));
        System.out.println("ID | Сотрудник | Место | Дата | Статус");
        if (bookings.isEmpty()) System.out.println("Бронирований нет.");
        bookings.forEach(b -> System.out.printf("%d | %s | %d | %s | %s%n",
            b.id(), names.get(b.employeeId()), b.spot(), b.date(), b.status()));
    }
}
