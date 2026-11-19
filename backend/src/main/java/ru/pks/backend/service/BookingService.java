package ru.pks.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pks.backend.config.AppProperties;
import ru.pks.backend.domain.Booking;
import ru.pks.backend.domain.BookingStatus;
import ru.pks.backend.domain.Employee;
import ru.pks.backend.dto.BookingDtos;
import ru.pks.backend.error.*;
import ru.pks.backend.filter.BookingFilter;
import ru.pks.backend.filter.BookingSpecifications;
import ru.pks.backend.repository.*;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class BookingService {
    private final BookingRepository bookings;
    private final EmployeeRepository employees;
    private final BookingNoteRepository notes;
    private final AuditService audit;
    private final UrgencyCalculator urgency;
    private final AppProperties properties;

    public BookingService(BookingRepository bookings, EmployeeRepository employees,
            BookingNoteRepository notes, AuditService audit, UrgencyCalculator urgency, AppProperties properties) {
        this.bookings = bookings;
        this.employees = employees;
        this.notes = notes;
        this.audit = audit;
        this.urgency = urgency;
        this.properties = properties;
    }

    public Page<BookingDtos.Response> list(BookingFilter filter, Pageable pageable) {
        return bookings.findAll(BookingSpecifications.from(filter, LocalDate.now()), pageable).map(this::response);
    }

    public BookingDtos.Response get(UUID id) { return response(require(id)); }

    public Booking require(UUID id) {
        return bookings.findByUuid(id).orElseThrow(() -> new NotFoundException("Бронирование не найдено"));
    }

    @Transactional
    public BookingDtos.Response save(UUID id, BookingDtos.SaveRequest request) {
        Booking booking = id == null ? new Booking() : require(id);
        if (id != null) {
            checkVersion(booking, request.version());
            if (booking.isArchived()) throw new ConflictException("Сначала восстановите бронь из архива");
            validateTransition(booking, request.status());
            if (!booking.getStatus().active()) throw new BadRequestException("Закрытую бронь нельзя редактировать");
        } else if (request.status() != BookingStatus.CREATED) {
            throw new BadRequestException("Новая бронь должна иметь статус CREATED");
        }
        Employee employee = employees.lockByUuid(request.employeeId())
                .orElseThrow(() -> new NotFoundException("Сотрудник не найден"));
        boolean detailsChanged = id == null || !booking.getDate().equals(request.date())
                || booking.getSpot() != request.spot() || !booking.getEmployee().getUuid().equals(request.employeeId());
        if (detailsChanged && request.date().isBefore(LocalDate.now()))
            throw new BadRequestException("Нельзя бронировать на прошедшую дату");
        if (request.status() == BookingStatus.COMPLETED && request.date().isAfter(LocalDate.now()))
            throw new BadRequestException("Нельзя завершить будущую бронь");
        if (!employee.isActive() && (id == null || detailsChanged))
            throw new BadRequestException("Сотрудник отключен");
        String code = request.code().strip();
        bookings.findByCode(code).ifPresent(other -> {
            if (!other.getUuid().equals(booking.getUuid())) throw new ConflictException("Номер брони уже используется");
        });
        booking.setCode(code);
        booking.setEmployee(employee);
        booking.setSpot(request.spot());
        booking.setDate(request.date());
        booking.setStatus(request.status());
        booking.setPurpose(request.purpose().strip());
        booking.setGuest(request.guest());
        booking.setTags(normalizeTags(request.tags()));
        validateAvailability(booking);
        bookings.saveAndFlush(booking);
        audit.record(id == null ? "BOOKING_CREATED" : "BOOKING_UPDATED", "BOOKING", booking.getUuid(),
                "Сохранена бронь " + code, Map.of());
        return response(booking);
    }

    @Transactional
    public BookingDtos.Response archive(UUID id, long version, boolean archived) {
        Booking booking = require(id);
        checkVersion(booking, version);
        booking.setArchived(archived);
        if (!archived) {
            employees.lockByUuid(booking.getEmployee().getUuid());
            if (booking.getStatus().active() && !booking.getEmployee().isActive())
                throw new ConflictException("Сотрудник отключен");
            validateAvailability(booking);
        }
        bookings.flush();
        audit.record(archived ? "BOOKING_ARCHIVED" : "BOOKING_RESTORED", "BOOKING", id,
                (archived ? "В архиве: " : "Восстановлена: ") + booking.getCode(), Map.of());
        return response(booking);
    }

    @Transactional
    public void permanentDelete(UUID id, long version) {
        if (!properties.permanentDeleteEnabled()) throw new BadRequestException("Окончательное удаление отключено");
        Booking booking = require(id);
        checkVersion(booking, version);
        if (!booking.isArchived()) throw new BadRequestException("Сначала отправьте бронь в архив");
        notes.deleteByBookingUuid(id);
        bookings.delete(booking);
        bookings.flush();
        audit.record("BOOKING_DELETED", "BOOKING", id, "Удалена бронь " + booking.getCode(), Map.of());
    }

    @Transactional
    public void bulkOne(UUID id, BookingDtos.BulkRequest request) {
        Booking booking = require(id);
        switch (request.action()) {
            case ARCHIVE -> { archive(id, booking.getVersion(), true); return; }
            case RESTORE -> { archive(id, booking.getVersion(), false); return; }
            case STATUS -> {
                if (booking.isArchived()) throw new BadRequestException("Бронь в архиве");
                if (request.status() == null) throw new BadRequestException("Укажите статус");
                validateTransition(booking, request.status());
                booking.setStatus(request.status());
                validateAvailability(booking);
            }
            case ADD_TAG -> {
                String tag = normalizeTag(request.tag());
                if (booking.getTags().size() >= 20 && !booking.getTags().contains(tag))
                    throw new BadRequestException("У брони уже 20 тегов");
                booking.getTags().add(tag);
            }
            case REMOVE_TAG -> booking.getTags().remove(normalizeTag(request.tag()));
        }
        bookings.flush();
        audit.record("BOOKING_BULK_" + request.action(), "BOOKING", id, "Массовое действие с " + booking.getCode(), Map.of());
    }

    private void validateTransition(Booking booking, BookingStatus next) {
        if (!booking.getStatus().canChangeTo(next)) throw new BadRequestException("Недопустимый переход статуса");
        if (next == BookingStatus.COMPLETED && booking.getDate().isAfter(LocalDate.now()))
            throw new BadRequestException("Нельзя завершить будущую бронь");
        if (next.active() && next != booking.getStatus() && booking.getDate().isBefore(LocalDate.now()))
            throw new BadRequestException("Нельзя подтвердить прошедшую бронь");
    }

    private void validateAvailability(Booking booking) {
        if (booking.isArchived() || !booking.getStatus().active()) return;
        // индексы postgres дополнительно защищают от одновременных запросов на одно место
        var conflicts = bookings.findAll((root, query, cb) -> cb.and(
                cb.notEqual(root.get("uuid"), booking.getUuid()), cb.isFalse(root.get("archived")),
                root.get("status").in(BookingStatus.CREATED, BookingStatus.CONFIRMED),
                cb.equal(root.get("date"), booking.getDate()),
                cb.or(cb.equal(root.get("spot"), booking.getSpot()),
                        cb.equal(root.get("employee").get("id"), booking.getEmployee().getId()))));
        if (!conflicts.isEmpty()) throw new ConflictException("Место или сотрудник уже заняты на эту дату");
    }

    private void checkVersion(Booking booking, long version) {
        if (booking.getVersion() != version) throw new ConflictException("Бронь уже изменена, обновите список");
    }

    private Set<String> normalizeTags(Set<String> tags) {
        Set<String> normalized = new LinkedHashSet<>();
        if (tags != null) for (String tag : tags) normalized.add(normalizeTag(tag));
        return normalized;
    }

    private String normalizeTag(String tag) {
        if (tag == null || tag.isBlank() || tag.strip().length() > 50)
            throw new BadRequestException("Тег должен содержать от 1 до 50 символов");
        return tag.strip().toLowerCase(Locale.ROOT);
    }

    public BookingDtos.Response response(Booking booking) {
        Employee employee = booking.getEmployee();
        return new BookingDtos.Response(booking.getUuid(), booking.getId(), booking.getCode(), employee.getUuid(),
                employee.getName(), employee.getEmail(), booking.getSpot(), booking.getDate(), booking.getStatus(),
                booking.getPurpose(), booking.isGuest(), Set.copyOf(booking.getTags()),
                urgency.calculate(booking.getStatus(), booking.getDate(), booking.isArchived(), LocalDate.now()),
                booking.isArchived(), booking.getCreatedAt(), booking.getUpdatedAt(), booking.getVersion());
    }
}
