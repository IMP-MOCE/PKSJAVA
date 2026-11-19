package ru.pks.backend;

import org.junit.jupiter.api.Test;
import ru.pks.backend.domain.BookingStatus;
import ru.pks.backend.domain.Urgency;
import ru.pks.backend.service.UrgencyCalculator;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class UrgencyCalculatorTest {
    private final UrgencyCalculator calculator = new UrgencyCalculator();
    private final LocalDate today = LocalDate.of(2026, 11, 16);

    @Test void calculatesUrgencyFromStatusDateAndArchive() {
        assertEquals(Urgency.OVERDUE, calculator.calculate(BookingStatus.CREATED, today.minusDays(1), false, today));
        assertEquals(Urgency.SOON, calculator.calculate(BookingStatus.CREATED, today, false, today));
        assertEquals(Urgency.SOON, calculator.calculate(BookingStatus.CREATED, today.plusDays(1), false, today));
        assertEquals(Urgency.NORMAL, calculator.calculate(BookingStatus.CREATED, today.plusDays(2), false, today));
        assertEquals(Urgency.NORMAL, calculator.calculate(BookingStatus.CONFIRMED, today.minusDays(1), false, today));
        assertEquals(Urgency.NORMAL, calculator.calculate(BookingStatus.CREATED, today.minusDays(1), true, today));
    }
}
