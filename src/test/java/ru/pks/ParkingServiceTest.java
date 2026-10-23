package ru.pks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import ru.pks.model.Booking;
import ru.pks.model.BookingStatus;
import ru.pks.model.Employee;
import ru.pks.repository.ParkingRepository;
import ru.pks.service.ParkingService;
import ru.pks.ui.ConsoleMenu;

// тесты правил сервиса без настоящей базы, вместо postgres тут MemoryRepository (он внизу файла)
// junit создаёт новый объект класса на каждый @Test, поэтому у каждого теста своё чистое хранилище
// в хранилище заранее лежат два сотрудника: id 1 Петров и id 2 Смирнова
class ParkingServiceTest {
    private final MemoryRepository memoryRepository = new MemoryRepository();
    private final ParkingService service = new ParkingService(memoryRepository);
    private final LocalDate today = LocalDate.now();

    // имя и почта проверяются, почта приводится к нижнему регистру, дубль почты не пускаем даже в другом регистре
    @Test void validatesEmployees() {
        assertThrows(IllegalArgumentException.class, () -> service.addEmployee(" ", "a@b.ru"));
        assertThrows(IllegalArgumentException.class, () -> service.addEmployee("Иван", "invalid"));
        var addedEmployee = service.addEmployee(" Иван ", " IVAN@COMPANY.RU ");
        assertEquals("Иван", addedEmployee.name());
        assertEquals("ivan@company.ru", addedEmployee.email());
        assertThrows(IllegalArgumentException.class, () -> service.addEmployee("Другой", "IVAN@COMPANY.RU"));
    }

    // кривые данные брони: несуществующий сотрудник, место вне 1..50, дата в прошлом, пустая дата
    // в конце проверяем, что ни одна из них не просочилась в хранилище
    @Test void validatesBookingFields() {
        assertThrows(IllegalArgumentException.class, () -> service.create(999, 1, today));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 0, today));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 51, today));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 1, today.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 1, null));
        assertTrue(memoryRepository.bookings.isEmpty());
    }

    // в один день на одно место только одна бронь, и у сотрудника тоже только одна бронь в день
    // а отменённая бронь место освобождает
    @Test void preventsConflictsAndReleasesCancelledSpot() {
        var petrovBooking = service.create(1, 1, today);
        // место 1 уже занято Петровым
        assertThrows(IllegalArgumentException.class, () -> service.create(2, 1, today));
        // у Петрова уже есть бронь на сегодня
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 2, today));
        // Петров переехал на место 2, и место 1 освободилось для Смирновой
        service.update(petrovBooking.id(), 2, today);
        service.create(2, 1, today);
        // а теперь обратно на 1 нельзя, оно у Смирновой
        assertThrows(IllegalArgumentException.class, () -> service.update(petrovBooking.id(), 1, today));
        // отменили бронь Петрова, и он снова может бронировать на сегодня
        service.changeStatus(petrovBooking.id(), BookingStatus.CANCELLED);
        service.create(1, 2, today);
        assertEquals(3, service.bookings().size());
    }

    // переходы статусов: CREATED -> CONFIRMED -> COMPLETED, перескакивать и откатываться нельзя
    @Test void enforcesLifecycle() {
        var booking = service.create(1, 1, today);
        // сразу из CREATED в COMPLETED нельзя
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(booking.id(), BookingStatus.COMPLETED));
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(booking.id(), null));
        service.changeStatus(booking.id(), BookingStatus.CONFIRMED);
        service.changeStatus(booking.id(), BookingStatus.COMPLETED);
        // из завершённой назад нельзя, и менять её тоже нельзя
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(booking.id(), BookingStatus.CREATED));
        assertThrows(IllegalArgumentException.class, () -> service.update(booking.id(), 2, today));
        // завершить бронь на завтра нельзя, она ещё не прошла
        var tomorrowBooking = service.create(1, 1, today.plusDays(1));
        service.changeStatus(tomorrowBooking.id(), BookingStatus.CONFIRMED);
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(tomorrowBooking.id(), BookingStatus.COMPLETED));
        // из отменённой обратно в подтверждённую тоже нельзя
        var cancelledBooking = service.create(2, 2, today);
        service.changeStatus(cancelledBooking.id(), BookingStatus.CANCELLED);
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(cancelledBooking.id(), BookingStatus.CONFIRMED));
    }

    // бронь на вчера можно отменить, хотя создать бронь на вчера нельзя
    // через сервис такую не создать, поэтому кладём её напрямую в хранилище
    @Test void allowsCancellingExpiredBooking() {
        var yesterdayBooking = memoryRepository.save(new Booking(0, 1, 1, today.minusDays(1), BookingStatus.CREATED));
        assertEquals(BookingStatus.CANCELLED, service.changeStatus(yesterdayBooking.id(), BookingStatus.CANCELLED).status());
    }

    // поиск, фильтры, сортировка, статистика и удаление
    @Test void searchesFiltersSortsAndCounts() {
        // Петров на место 2 через 2 дня, Смирнова на место 3 сегодня (подтверждена)
        var laterBooking = service.create(1, 2, today.plusDays(2));
        var earlierBooking = service.create(2, 3, today);
        service.changeStatus(earlierBooking.id(), BookingStatus.CONFIRMED);
        // поиск по фио не зависит от регистра
        assertEquals(List.of(laterBooking), service.searchEmployee("ПЕТР"));
        assertEquals(List.of(laterBooking), service.searchSpot(2));
        assertEquals(1, service.filterStatus(BookingStatus.CONFIRMED).size());
        assertEquals(1, service.filterDates(today, today).size());
        assertThrows(IllegalArgumentException.class, () -> service.filterDates(today.plusDays(1), today));
        // по дате первая сегодняшняя, по месту первое место 2
        assertEquals(earlierBooking.id(), service.sorted(true).getFirst().id());
        assertEquals(laterBooking.id(), service.sorted(false).getFirst().id());
        assertEquals(2L, service.statistics().get("Бронирований"));
        assertEquals(1L, service.statistics().get("Занято мест сегодня"));
        assertEquals(7, service.statistics().size());
        service.delete(laterBooking.id());
        assertThrows(IllegalArgumentException.class, () -> service.get(laterBooking.id()));
        assertThrows(IllegalArgumentException.class, () -> service.get(0));
    }

    // консольное меню не должно падать от мусорного ввода
    // вместо клавиатуры подсовываем Scanner со строкой, каждая \n это как нажать enter
    @Test void menuSurvivesInvalidInputAndEndOfInput() {
        // по порядку: бронь по id "abc", бронь по id 999, бронь с кривой датой, фильтр по кривому статусу, статистика, выход
        String typedCommands = "4\nabc\n4\n999\n5\n1\n1\nbad-date\n11\nbad-status\n15\n0\n";
        assertDoesNotThrow(() -> new ConsoleMenu(service, new Scanner(typedCommands)).run());
        // ввод оборвался посреди пункта 5, меню должно просто тихо выйти
        assertDoesNotThrow(() -> new ConsoleMenu(service, new Scanner("5\n")).run());
    }

    // фейковое хранилище в памяти вместо postgres, ведёт себя как настоящий репозиторий, только всё в списках
    // static, чтобы его можно было брать и из других тестов (BookingSearchTest так и делает)
    static final class MemoryRepository implements ParkingRepository {
        private final List<Employee> employees = new ArrayList<>(List.of(
            new Employee(1, "Петров", "p@company.ru"), new Employee(2, "Смирнова", "s@company.ru")));
        private final List<Booking> bookings = new ArrayList<>();
        // счётчик id, как автоинкремент в базе
        private long nextBookingId = 1;
        @Override
        public List<Employee> employees() { return List.copyOf(employees); }
        @Override
        public Employee addEmployee(String name, String email) {
            var newEmployee = new Employee(employees.size() + 1, name, email);
            employees.add(newEmployee);
            return newEmployee;
        }
        @Override
        public List<Booking> bookings() { return List.copyOf(bookings); }
        @Override
        public Booking save(Booking booking) {
            // обновление = выкинули старую версию и положили новую
            if (booking.id() != 0) bookings.removeIf(storedBooking -> storedBooking.id() == booking.id());
            var savedBooking = new Booking(booking.id() == 0 ? nextBookingId++ : booking.id(),
                booking.employeeId(), booking.spot(), booking.date(), booking.status());
            bookings.add(savedBooking);
            return savedBooking;
        }
        @Override
        public boolean delete(long id) { return bookings.removeIf(booking -> booking.id() == id); }
        @Override
        public List<String> tables() { return List.of("employees", "bookings"); }
    }
}
