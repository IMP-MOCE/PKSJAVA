package ru.pks.model;

import java.time.LocalDate;

// одна бронь = одна строка из таблицы bookings
// id = 0 значит что брони ещё нет в бд, настоящий id выдаст база при вставке
public record Booking(long id, long employeeId, int spot, LocalDate date, BookingStatus status) { }
