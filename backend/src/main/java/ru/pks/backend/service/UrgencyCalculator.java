package ru.pks.backend.service;

import org.springframework.stereotype.Component;
import ru.pks.backend.domain.BookingStatus;
import ru.pks.backend.domain.Urgency;
import java.time.LocalDate;

@Component
public class UrgencyCalculator {
    public Urgency calculate(BookingStatus status, LocalDate date, boolean archived, LocalDate today) {
        if (archived || status != BookingStatus.CREATED) return Urgency.NORMAL;
        if (date.isBefore(today)) return Urgency.OVERDUE;
        if (!date.isAfter(today.plusDays(1))) return Urgency.SOON;
        return Urgency.NORMAL;
    }
}
