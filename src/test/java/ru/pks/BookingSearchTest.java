package ru.pks;

import org.junit.jupiter.api.Test;
import ru.pks.model.BookingStatus;
import ru.pks.service.ParkingService;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

// тест главного поиска из окна (ParkingService.search), где текст, статус и период работают вместе
class BookingSearchTest {
    @Test void combinesTextStatusAndInclusiveDateFilters() {
        // хранилище в памяти берём из ParkingServiceTest, там Петров (id 1) и Смирнова (id 2)
        var service = new ParkingService(new ParkingServiceTest.MemoryRepository());
        var today = LocalDate.now();
        // Петров на место 24 сегодня (подтверждена), Смирнова на место 25 завтра
        var petrovBooking = service.create(1, 24, today);
        service.create(2, 25, today.plusDays(1));
        service.changeStatus(petrovBooking.id(), BookingStatus.CONFIRMED);
        // все фильтры сразу, и период today..today включает сам день
        assertEquals(1, service.search("ПЕТР", BookingStatus.CONFIRMED, today, today).size());
        // поиск по куску почты
        assertEquals(1, service.search("p@company", null, null, null).size());
        // число в строке поиска ищет по номеру места
        assertEquals(1, service.search("24", null, null, null).size());
        // текст подходит, а статус нет, значит пусто
        assertTrue(service.search("ПЕТР", BookingStatus.CREATED, null, null).isEmpty());
        // пустые фильтры отдают всё
        assertEquals(2, service.search("", null, null, null).size());
        // начало периода позже конца это ошибка
        assertThrows(IllegalArgumentException.class, () -> service.search("", null, today.plusDays(1), today));
    }
}
