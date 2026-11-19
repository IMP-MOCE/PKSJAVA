package ru.pks.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import ru.pks.backend.dto.BookingDtos;
import ru.pks.backend.error.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.UUID;

@Service
public class BulkService {
    private final BookingService bookings;

    public BulkService(BookingService bookings) { this.bookings = bookings; }

    public BookingDtos.BulkResult execute(BookingDtos.BulkRequest request) {
        var successful = new ArrayList<UUID>();
        var errors = new ArrayList<BookingDtos.BulkError>();
        // каждый вызов сервиса получает свою транзакцию и свой результат
        for (UUID id : new LinkedHashSet<>(request.ids())) {
            try {
                bookings.bulkOne(id, request);
                successful.add(id);
            } catch (BadRequestException | NotFoundException | ConflictException ex) {
                errors.add(new BookingDtos.BulkError(id, ex.getMessage()));
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
                errors.add(new BookingDtos.BulkError(id, "Данные изменились или конфликтуют с другой бронью"));
            }
        }
        return new BookingDtos.BulkResult(successful, errors);
    }
}
