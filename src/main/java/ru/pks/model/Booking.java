package ru.pks.model;

import java.time.LocalDate;

public record Booking(long id, long employeeId, int spot, LocalDate date, BookingStatus status) { }
