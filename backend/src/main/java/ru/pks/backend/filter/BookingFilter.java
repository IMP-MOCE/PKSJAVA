package ru.pks.backend.filter;

import ru.pks.backend.domain.BookingStatus;
import ru.pks.backend.domain.Urgency;
import java.time.LocalDate;
import java.util.UUID;

public record BookingFilter(String q, UUID employeeId, BookingStatus status, Boolean guest,
        Urgency urgency, String tag, Boolean archived, Integer spot, String spotOp,
        LocalDate from, LocalDate to) { }
