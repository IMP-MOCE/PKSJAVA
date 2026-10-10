package ru.pks;

import org.junit.jupiter.api.Test;
import ru.pks.model.BookingStatus;
import ru.pks.service.ParkingService;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class BookingSearchTest {
    @Test void combinesTextStatusAndInclusiveDateFilters() {
        var service = new ParkingService(new ParkingServiceTest.MemoryRepository());
        var today = LocalDate.now();
        var first = service.create(1, 24, today);
        service.create(2, 25, today.plusDays(1));
        service.changeStatus(first.id(), BookingStatus.CONFIRMED);
        assertEquals(1, service.search("ПЕТР", BookingStatus.CONFIRMED, today, today).size());
        assertEquals(1, service.search("p@company", null, null, null).size());
        assertEquals(1, service.search("24", null, null, null).size());
        assertTrue(service.search("ПЕТР", BookingStatus.CREATED, null, null).isEmpty());
        assertEquals(2, service.search("", null, null, null).size());
        assertThrows(IllegalArgumentException.class, () -> service.search("", null, today.plusDays(1), today));
    }
}
