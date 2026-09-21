package ru.pks;

import org.junit.jupiter.api.Test;
import ru.pks.model.*;
import ru.pks.repository.ParkingRepository;
import ru.pks.service.ParkingService;
import ru.pks.ui.ConsoleMenu;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ParkingServiceTest {
    private final MemoryRepository repo = new MemoryRepository();
    private final ParkingService service = new ParkingService(repo);
    private final LocalDate today = LocalDate.now();

    @Test void validatesEmployees() {
        assertThrows(IllegalArgumentException.class, () -> service.addEmployee(" ", "a@b.ru"));
        assertThrows(IllegalArgumentException.class, () -> service.addEmployee("Иван", "invalid"));
        var employee = service.addEmployee(" Иван ", " IVAN@COMPANY.RU ");
        assertEquals("Иван", employee.name());
        assertEquals("ivan@company.ru", employee.email());
        assertThrows(IllegalArgumentException.class, () -> service.addEmployee("Другой", "IVAN@COMPANY.RU"));
    }

    @Test void validatesBookingFields() {
        assertThrows(IllegalArgumentException.class, () -> service.create(999, 1, today));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 0, today));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 51, today));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 1, today.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 1, null));
        assertTrue(repo.bookings.isEmpty());
    }

    @Test void preventsConflictsAndReleasesCancelledSpot() {
        var b = service.create(1, 1, today);
        assertThrows(IllegalArgumentException.class, () -> service.create(2, 1, today));
        assertThrows(IllegalArgumentException.class, () -> service.create(1, 2, today));
        service.update(b.id(), 2, today);
        service.create(2, 1, today);
        assertThrows(IllegalArgumentException.class, () -> service.update(b.id(), 1, today));
        service.changeStatus(b.id(), BookingStatus.CANCELLED);
        service.create(1, 2, today);
        assertEquals(3, service.bookings().size());
    }

    @Test void enforcesLifecycle() {
        var b = service.create(1, 1, today);
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(b.id(), BookingStatus.COMPLETED));
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(b.id(), null));
        service.changeStatus(b.id(), BookingStatus.CONFIRMED);
        service.changeStatus(b.id(), BookingStatus.COMPLETED);
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(b.id(), BookingStatus.CREATED));
        assertThrows(IllegalArgumentException.class, () -> service.update(b.id(), 2, today));
        var future = service.create(1, 1, today.plusDays(1));
        service.changeStatus(future.id(), BookingStatus.CONFIRMED);
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(future.id(), BookingStatus.COMPLETED));
        var cancelled = service.create(2, 2, today);
        service.changeStatus(cancelled.id(), BookingStatus.CANCELLED);
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(cancelled.id(), BookingStatus.CONFIRMED));
    }

    @Test void allowsCancellingExpiredBooking() {
        var b = repo.save(new Booking(0, 1, 1, today.minusDays(1), BookingStatus.CREATED));
        assertEquals(BookingStatus.CANCELLED, service.changeStatus(b.id(), BookingStatus.CANCELLED).status());
    }

    @Test void searchesFiltersSortsAndCounts() {
        var later = service.create(1, 2, today.plusDays(2));
        var earlier = service.create(2, 3, today);
        service.changeStatus(earlier.id(), BookingStatus.CONFIRMED);
        assertEquals(List.of(later), service.searchEmployee("ПЕТР"));
        assertEquals(List.of(later), service.searchSpot(2));
        assertEquals(1, service.filterStatus(BookingStatus.CONFIRMED).size());
        assertEquals(1, service.filterDates(today, today).size());
        assertThrows(IllegalArgumentException.class, () -> service.filterDates(today.plusDays(1), today));
        assertEquals(earlier.id(), service.sorted(true).getFirst().id());
        assertEquals(later.id(), service.sorted(false).getFirst().id());
        assertEquals(2L, service.statistics().get("Бронирований"));
        assertEquals(1L, service.statistics().get("Занято мест сегодня"));
        assertEquals(7, service.statistics().size());
        service.delete(later.id());
        assertThrows(IllegalArgumentException.class, () -> service.get(later.id()));
        assertThrows(IllegalArgumentException.class, () -> service.get(0));
    }

    @Test void menuSurvivesInvalidInputAndEndOfInput() {
        String commands = "4\nabc\n4\n999\n5\n1\n1\nbad-date\n11\nbad-status\n15\n0\n";
        assertDoesNotThrow(() -> new ConsoleMenu(service, new Scanner(commands)).run());
        assertDoesNotThrow(() -> new ConsoleMenu(service, new Scanner("5\n")).run());
    }

    static final class MemoryRepository implements ParkingRepository {
        private final List<Employee> employees = new ArrayList<>(List.of(
            new Employee(1, "Петров", "p@company.ru"), new Employee(2, "Смирнова", "s@company.ru")));
        private final List<Booking> bookings = new ArrayList<>();
        private long nextId = 1;
        public List<Employee> employees() { return List.copyOf(employees); }
        public Employee addEmployee(String name, String email) {
            var e = new Employee(employees.size() + 1, name, email);
            employees.add(e);
            return e;
        }
        public List<Booking> bookings() { return List.copyOf(bookings); }
        public Booking save(Booking b) {
            if (b.id() != 0) bookings.removeIf(old -> old.id() == b.id());
            var saved = new Booking(b.id() == 0 ? nextId++ : b.id(), b.employeeId(), b.spot(), b.date(), b.status());
            bookings.add(saved);
            return saved;
        }
        public boolean delete(long id) { return bookings.removeIf(b -> b.id() == id); }
        public List<String> tables() { return List.of("employees", "bookings"); }
    }
}
