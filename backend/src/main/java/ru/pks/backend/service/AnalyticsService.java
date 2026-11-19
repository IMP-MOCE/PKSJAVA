package ru.pks.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pks.backend.domain.Booking;
import ru.pks.backend.repository.BookingRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AnalyticsService {
    private final BookingRepository bookings;
    private final UrgencyCalculator urgency;

    public AnalyticsService(BookingRepository bookings, UrgencyCalculator urgency) {
        this.bookings = bookings;
        this.urgency = urgency;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        var all = bookings.findAll();
        long archived = 0;
        long guests = 0;
        long occupiedToday = 0;
        long active = 0;
        long open = 0;
        long spotSum = 0;
        Map<String, Long> byEmployee = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byUrgency = new LinkedHashMap<>();
        for (Booking booking : all) {
            if (booking.isArchived()) archived++;
            if (booking.isGuest()) guests++;
            if (!booking.isArchived()) active++;
            if (!booking.isArchived() && booking.getStatus().active()) open++;
            if (!booking.isArchived() && booking.getStatus().active() && booking.getDate().equals(LocalDate.now())) occupiedToday++;
            spotSum += booking.getSpot();
            byEmployee.merge(booking.getEmployee().getName() + " (" + booking.getEmployee().getEmail() + ")", 1L, Long::sum);
            byStatus.merge(booking.getStatus().name(), 1L, Long::sum);
            byUrgency.merge(urgency.calculate(booking.getStatus(), booking.getDate(), booking.isArchived(), LocalDate.now()).name(), 1L, Long::sum);
        }
        return new Summary(all.size(), active, open, archived, guests, all.isEmpty() ? 0 : (double) spotSum / all.size(),
                occupiedToday, 50 - occupiedToday, byEmployee, byStatus, byUrgency);
    }

    public record Summary(long total, long active, long open, long archived, long guests, double averageSpot,
            long occupiedToday, long freeToday, Map<String, Long> byEmployee,
            Map<String, Long> byStatus, Map<String, Long> byUrgency) { }
}
