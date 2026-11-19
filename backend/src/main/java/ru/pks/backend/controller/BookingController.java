package ru.pks.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.pks.backend.dto.*;
import ru.pks.backend.domain.BookingStatus;
import ru.pks.backend.domain.Urgency;
import ru.pks.backend.filter.BookingFilter;
import ru.pks.backend.service.*;
import ru.pks.backend.web.PageableFactory;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class BookingController {
    private final BookingService bookings;
    private final BulkService bulk;
    private final CsvBookingService csv;
    private final PageableFactory pages;

    public BookingController(BookingService bookings, BulkService bulk, CsvBookingService csv, PageableFactory pages) {
        this.bookings = bookings;
        this.bulk = bulk;
        this.csv = csv;
        this.pages = pages;
    }

    @GetMapping
    public PageResponse<BookingDtos.Response> list(
            @RequestParam(defaultValue = "") String q, @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) BookingStatus status, @RequestParam(required = false) Boolean guest,
            @RequestParam(required = false) Urgency urgency, @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") Boolean archived, @RequestParam(required = false) Integer spot,
            @RequestParam(defaultValue = "eq") String spotOp, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "date,desc") String sort) {
        return PageResponse.from(bookings.list(new BookingFilter(q, employeeId, status, guest, urgency, tag,
                archived, spot, spotOp, from, to), pages.bookings(page, size, sort)));
    }

    @GetMapping("/{id}")
    public BookingDtos.Response get(@PathVariable UUID id) { return bookings.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public BookingDtos.Response create(@Valid @RequestBody BookingDtos.SaveRequest request) {
        return bookings.save(null, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public BookingDtos.Response update(@PathVariable UUID id, @Valid @RequestBody BookingDtos.SaveRequest request) {
        return bookings.save(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public BookingDtos.Response archive(@PathVariable UUID id, @RequestParam long version) {
        return bookings.archive(id, version, true);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public BookingDtos.Response restore(@PathVariable UUID id, @Valid @RequestBody BookingDtos.VersionRequest request) {
        return bookings.archive(id, request.version(), false);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void permanent(@PathVariable UUID id, @RequestParam long version) { bookings.permanentDelete(id, version); }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public BookingDtos.BulkResult bulk(@Valid @RequestBody BookingDtos.BulkRequest request) { return bulk.execute(request); }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public ImportDtos.Preview preview(@RequestParam MultipartFile file,
            @RequestParam(defaultValue = "NEW_ONLY") ImportDtos.Mode mode) { return csv.preview(file, mode); }

    @PostMapping("/import/apply")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public ImportDtos.Applied apply(@Valid @RequestBody ImportDtos.ApplyRequest request) { return csv.apply(request.previewId()); }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "") String q, @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) BookingStatus status, @RequestParam(required = false) Boolean guest,
            @RequestParam(required = false) Urgency urgency, @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") Boolean archived, @RequestParam(required = false) Integer spot,
            @RequestParam(defaultValue = "eq") String spotOp, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "date,desc") String sort) {
        byte[] bytes = csv.export(new BookingFilter(q, employeeId, status, guest, urgency, tag,
                archived, spot, spotOp, from, to), pages.bookings(0, 100, sort).getSort());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=parking.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(bytes);
    }
}
