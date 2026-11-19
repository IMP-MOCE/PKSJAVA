package ru.pks.backend.service;

import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import ru.pks.backend.config.AppProperties;
import ru.pks.backend.domain.BookingStatus;
import ru.pks.backend.dto.BookingDtos;
import ru.pks.backend.dto.ImportDtos;
import ru.pks.backend.error.*;
import ru.pks.backend.filter.BookingFilter;
import ru.pks.backend.repository.BookingRepository;
import ru.pks.backend.repository.EmployeeRepository;
import ru.pks.backend.security.CurrentUserService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CsvBookingService {
    private static final String[] HEADERS = {"code", "employeeEmail", "spot", "date", "status", "purpose", "guest", "tags"};
    private final BookingService service;
    private final BookingRepository bookings;
    private final EmployeeRepository employees;
    private final CurrentUserService currentUser;
    private final Validator validator;
    private final TransactionTemplate transaction;
    private final AppProperties properties;
    private final Map<UUID, PendingImport> pending = new ConcurrentHashMap<>();

    public CsvBookingService(BookingService service, BookingRepository bookings, EmployeeRepository employees,
            CurrentUserService currentUser, Validator validator, TransactionTemplate transaction, AppProperties properties) {
        this.service = service;
        this.bookings = bookings;
        this.employees = employees;
        this.currentUser = currentUser;
        this.validator = validator;
        this.transaction = transaction;
        this.properties = properties;
    }

    public byte[] export(BookingFilter filter, org.springframework.data.domain.Sort sort) {
        return transaction.execute(tx -> {
            var found = bookings.findAll(ru.pks.backend.filter.BookingSpecifications.from(filter, LocalDate.now()), sort);
            if (found.size() > 10000) throw new BadRequestException("Сузьте фильтр до 10000 записей");
            try (var writer = new StringWriter(); var csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(HEADERS).get())) {
                for (var booking : found) {
                    csv.printRecord(safeText(booking.getCode()), safeText(booking.getEmployee().getEmail()), booking.getSpot(), booking.getDate(),
                            booking.getStatus(), safeText(booking.getPurpose()), booking.isGuest(), safeText(String.join("|", booking.getTags())));
                }
                csv.flush();
                return ("\uFEFF" + writer).getBytes(StandardCharsets.UTF_8);
            } catch (IOException ex) { throw new BadRequestException("Не удалось создать CSV"); }
        });
    }

    public ImportDtos.Preview preview(MultipartFile file, ImportDtos.Mode mode) {
        if (file.isEmpty()) throw new BadRequestException("Выберите непустой CSV-файл");
        UUID owner = currentUser.requireEntity().getId();
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        pending.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        if (pending.size() >= 100) throw new BadRequestException("Слишком много ожидающих импортов, повторите позже");
        var rows = new ArrayList<ImportRow>();
        var errors = new ArrayList<ImportDtos.RowError>();
        var codes = new HashSet<String>();
        var spotDates = new HashSet<String>();
        var employeeDates = new HashSet<String>();
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            reader.mark(1);
            if (reader.read() != '\uFEFF') reader.reset();
            try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
                for (String header : HEADERS)
                    if (!parser.getHeaderMap().containsKey(header)) throw new BadRequestException("В CSV нет столбца " + header);
                for (var row : parser) {
                    int line = (int) row.getRecordNumber() + 1;
                    if (line > properties.importLimits().maxRows() + 1) throw new BadRequestException("В CSV должно быть не больше " + properties.importLimits().maxRows() + " строк");
                    try {
                        String code = originalText(row.get("code")).strip();
                        if (!codes.add(code)) throw new BadRequestException("Номер брони повторяется в файле");
                        var employee = employees.findByEmailIgnoreCase(originalText(row.get("employeeEmail")).strip())
                                .orElseThrow(() -> new BadRequestException("Сотрудник с такой почтой не найден"));
                        var existing = bookings.findByCode(code).orElse(null);
                        if (existing != null && mode == ImportDtos.Mode.NEW_ONLY) throw new BadRequestException("Номер брони уже существует");
                        String guestText = row.get("guest").strip();
                        if (!guestText.equalsIgnoreCase("true") && !guestText.equalsIgnoreCase("false"))
                            throw new BadRequestException("guest должен быть true или false");
                        Set<String> tags = new LinkedHashSet<>();
                        if (!row.get("tags").isBlank()) for (String tag : originalText(row.get("tags")).split("\\|")) tags.add(tag.strip());
                        String purpose = originalText(row.get("purpose"));
                        var request = new BookingDtos.SaveRequest(code, employee.getUuid(), Integer.parseInt(row.get("spot")),
                                LocalDate.parse(row.get("date")), BookingStatus.valueOf(row.get("status")), purpose,
                                Boolean.parseBoolean(guestText), tags, existing == null ? 0 : existing.getVersion());
                        var violations = validator.validate(request);
                        if (!violations.isEmpty()) throw new BadRequestException(violations.iterator().next().getPropertyPath() + ": " + violations.iterator().next().getMessage());
                        var planned = new ImportRow(line, existing == null ? null : existing.getUuid(), request);
                        // проверка выполняет те же правила, но откатывает запись и аудит
                        transaction.execute(tx -> {
                            service.save(planned.id(), request);
                            tx.setRollbackOnly();
                            return null;
                        });
                        String placeKey = request.spot() + "/" + request.date();
                        String employeeKey = request.employeeId() + "/" + request.date();
                        if (request.status().active()) {
                            if (spotDates.contains(placeKey) || employeeDates.contains(employeeKey))
                                throw new BadRequestException("Двойное бронирование внутри файла");
                            spotDates.add(placeKey);
                            employeeDates.add(employeeKey);
                        }
                        rows.add(planned);
                    } catch (BadRequestException | ConflictException | NotFoundException ex) {
                        errors.add(new ImportDtos.RowError(line, ex.getMessage()));
                    } catch (IllegalArgumentException | java.time.DateTimeException ex) {
                        errors.add(new ImportDtos.RowError(line, "Проверьте число, дату и статус"));
                    } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
                        errors.add(new ImportDtos.RowError(line, "Данные конфликтуют с существующей бронью"));
                    }
                }
            }
        } catch (IOException | UncheckedIOException ex) { throw new BadRequestException("Не удалось прочитать CSV"); }
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        pending.put(id, new PendingImport(owner, List.copyOf(rows), List.copyOf(errors), expiresAt));
        return new ImportDtos.Preview(id, rows.size(), errors.size(), errors, expiresAt);
    }

    public ImportDtos.Applied apply(UUID previewId) {
        UUID owner = currentUser.requireEntity().getId();
        PendingImport plan = pending.get(previewId);
        if (plan == null || !plan.owner().equals(owner) || plan.expiresAt().isBefore(Instant.now()))
            throw new BadRequestException("Проверка импорта не найдена или устарела, проверьте файл заново");
        if (!pending.remove(previewId, plan)) throw new ConflictException("Этот импорт уже применяется");
        int successful = 0;
        var errors = new ArrayList<>(plan.errors());
        for (ImportRow row : plan.rows()) {
            try {
                service.save(row.id(), row.request());
                successful++;
            } catch (BadRequestException | ConflictException | NotFoundException ex) {
                errors.add(new ImportDtos.RowError(row.line(), ex.getMessage()));
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
                errors.add(new ImportDtos.RowError(row.line(), "Данные изменились или конфликтуют с другой бронью"));
            }
        }
        return new ImportDtos.Applied(successful, errors);
    }

    private String safeText(String text) {
        String trimmed = text.stripLeading();
        boolean formula = !trimmed.isEmpty() && "=+-@'".indexOf(trimmed.charAt(0)) >= 0;
        return formula || text.startsWith("\t") || text.startsWith("\r") ? "'" + text : text;
    }

    private String originalText(String text) {
        if (text.startsWith("'") && safeText(text.substring(1)).equals(text)) return text.substring(1);
        return text;
    }

    private record ImportRow(int line, UUID id, BookingDtos.SaveRequest request) { }
    private record PendingImport(UUID owner, List<ImportRow> rows, List<ImportDtos.RowError> errors, Instant expiresAt) { }
}
