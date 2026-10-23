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

// старая консольная версия из кр1, запускается с флагом --console
// по кругу печатаем меню, читаем номер пункта и зовём нужный метод сервиса
// Scanner передаётся снаружи, поэтому в тестах вместо клавиатуры можно подсунуть готовую строку с командами
public final class ConsoleMenu {
    private final ParkingService service;
    private final Scanner input;

    public ConsoleMenu(ParkingService service, Scanner input) {
        this.service = service;
        this.input = input;
    }

    // главный цикл меню, выходим по пункту 0 или когда закончился ввод
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
                // каждый пункт это одна строчка: спросили что нужно, вызвали сервис, вывели результат
                switch (read("Действие: ")) {
                    case "0" -> { return; }
                    case "1" -> service.employees().forEach(employee ->
                        System.out.printf("%d | %s | %s%n", employee.id(), employee.name(), employee.email()));
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
                        Path exportFile = Path.of("export", "parking.xlsx");
                        ExcelExporter.export(exportFile, service.employees(), service.bookings());
                        System.out.println("Сохранено: " + exportFile.toAbsolutePath());
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
            // ловим ошибки здесь, чтобы кривой ввод не ронял прогу, а просто печатал сообщение и показывал меню заново
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите целое число допустимого размера.");
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: дата должна быть в формате ГГГГ-ММ-ДД.");
            } catch (IllegalArgumentException | DataAccessException e) {
                System.out.println("Ошибка: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("Не удалось сохранить Excel: " + e.getMessage());
            } catch (NoSuchElementException e) {
                // это Scanner говорит, что ввод закончился (ctrl+z или конец строки в тесте), просто выходим
                return;
            }
        }
    }

    // печатает подсказку и читает строку без пробелов по краям
    private String read(String prompt) {
        System.out.print(prompt);
        return input.nextLine().strip();
    }

    // мелкие помощники для ввода, если ввели не число или не дату, вылетит исключение, его поймает run()
    private long number(String prompt) { return Long.parseLong(read(prompt)); }
    private long id() { return number("ID бронирования: "); }
    private int spot() { return Integer.parseInt(read("Место (1–50): ")); }
    private LocalDate date(String label) { return LocalDate.parse(read(label + " (ГГГГ-ММ-ДД): ")); }

    // читаем статус текстом, регистр не важен: и created, и CREATED подойдут
    private BookingStatus status() {
        String statusText = read("Статус (CREATED / CONFIRMED / COMPLETED / CANCELLED): ");
        try { return BookingStatus.valueOf(statusText.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Неизвестный статус."); }
    }

    // печатает брони табличкой, вместо id сотрудника показываем его имя
    private void display(List<Booking> bookings) {
        // мапа id сотрудника -> имя, чтобы не искать сотрудника заново для каждой строки
        Map<Long, String> employeeNamesById = new HashMap<>();
        service.employees().forEach(employee -> employeeNamesById.put(employee.id(), employee.name()));
        System.out.println("ID | Сотрудник | Место | Дата | Статус");
        if (bookings.isEmpty()) System.out.println("Бронирований нет.");
        bookings.forEach(booking -> System.out.printf("%d | %s | %d | %s | %s%n",
            booking.id(), employeeNamesById.get(booking.employeeId()), booking.spot(), booking.date(), booking.status()));
    }
}
