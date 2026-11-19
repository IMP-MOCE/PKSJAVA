package ru.pks.backend.web;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.pks.backend.domain.BookingStatus;
import ru.pks.backend.dto.BookingDtos;
import ru.pks.backend.dto.NoteDtos;
import ru.pks.backend.error.BadRequestException;
import ru.pks.backend.error.NotFoundException;
import ru.pks.backend.filter.BookingFilter;
import ru.pks.backend.service.*;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/bookings")
public class WebBookingController {
    private final BookingService bookings;
    private final EmployeeService employees;
    private final NoteService notes;
    private final BulkService bulk;
    private final CsvBookingService csv;
    private final PageableFactory pages;

    public WebBookingController(BookingService bookings, EmployeeService employees, NoteService notes,
            BulkService bulk, CsvBookingService csv, PageableFactory pages) {
        this.bookings = bookings;
        this.employees = employees;
        this.notes = notes;
        this.bulk = bulk;
        this.csv = csv;
        this.pages = pages;
    }

    @GetMapping
    public String list(@ModelAttribute BookingFilter filter, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size, @RequestParam(defaultValue = "date,desc") String sort,
            Model model) {
        var result = bookings.list(withArchiveDefault(filter), pages.bookings(page, size, sort));
        if (page > 0 && result.isEmpty()) result = bookings.list(withArchiveDefault(filter), pages.bookings(0, size, sort));
        model.addAttribute("result", result);
        model.addAttribute("employees", employees.list());
        model.addAttribute("sort", sort);
        return "bookings";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@ModelAttribute BookingFilter filter,
            @RequestParam(defaultValue = "date,desc") String sort) {
        byte[] bytes = csv.export(withArchiveDefault(filter), pages.bookings(0, 25, sort).getSort());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=parking.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(bytes);
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String createForm(Model model) {
        model.addAttribute("newCode", "P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("employees", employees.list());
        model.addAttribute("statuses", List.of(BookingStatus.CREATED));
        return "booking-form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String editForm(@PathVariable UUID id, Model model) {
        var booking = bookings.get(id);
        if (booking.archived() || !booking.status().active()) throw new BadRequestException("Эту бронь нельзя редактировать");
        var statuses = new ArrayList<BookingStatus>();
        for (BookingStatus status : BookingStatus.values()) {
            if (booking.status().canChangeTo(status)) statuses.add(status);
        }
        model.addAttribute("booking", booking);
        model.addAttribute("employees", employees.list());
        model.addAttribute("statuses", statuses);
        return "booking-form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String create(@Valid @ModelAttribute BookingDtos.SaveRequest form, RedirectAttributes redirect) {
        return save(null, form, redirect);
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute BookingDtos.SaveRequest form,
            RedirectAttributes redirect) {
        return save(id, form, redirect);
    }

    private String save(UUID id, BookingDtos.SaveRequest form, RedirectAttributes redirect) {
        bookings.save(id, form);
        redirect.addFlashAttribute("message", "Бронь сохранена");
        return "redirect:/bookings";
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String archive(@PathVariable UUID id, @Valid @ModelAttribute BookingDtos.VersionRequest form,
            RedirectAttributes redirect) {
        bookings.archive(id, form.version(), true);
        redirect.addFlashAttribute("message", "Бронь в архиве");
        return "redirect:/bookings";
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String restore(@PathVariable UUID id, @Valid @ModelAttribute BookingDtos.VersionRequest form,
            RedirectAttributes redirect) {
        bookings.archive(id, form.version(), false);
        redirect.addFlashAttribute("message", "Бронь восстановлена");
        return "redirect:/bookings?archived=true";
    }

    @GetMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDelete(@PathVariable UUID id, Model model) {
        model.addAttribute("booking", bookings.get(id));
        return "delete-booking";
    }

    @PostMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable UUID id, @Valid @ModelAttribute BookingDtos.VersionRequest form,
            RedirectAttributes redirect) {
        bookings.permanentDelete(id, form.version());
        redirect.addFlashAttribute("message", "Бронь удалена");
        return "redirect:/bookings?archived=true";
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String bulk(@Valid @ModelAttribute BookingDtos.BulkRequest form,
            @RequestParam(defaultValue = "false") boolean archived, RedirectAttributes redirect) {
        redirect.addFlashAttribute("bulkResult", bulk.execute(form));
        return archived ? "redirect:/bookings?archived=true" : "redirect:/bookings";
    }

    @GetMapping("/{id}/notes")
    public String notes(@PathVariable UUID id, @RequestParam(required = false) UUID edit, Model model) {
        var history = notes.list(id);
        model.addAttribute("booking", bookings.get(id));
        model.addAttribute("notes", history);
        if (edit != null) {
            model.addAttribute("editing", history.stream().filter(note -> note.id().equals(edit)).findFirst()
                    .orElseThrow(() -> new NotFoundException("Заметка не найдена")));
        }
        return "notes";
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String saveNote(@PathVariable UUID id, @RequestParam(required = false) UUID noteId,
            @Valid @ModelAttribute NoteDtos.SaveRequest form, RedirectAttributes redirect) {
        notes.save(id, noteId, form);
        redirect.addAttribute("id", id);
        return "redirect:/bookings/{id}/notes";
    }

    @PostMapping("/{id}/notes/{noteId}/delete")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String deleteNote(@PathVariable UUID id, @PathVariable UUID noteId, RedirectAttributes redirect) {
        notes.delete(id, noteId);
        redirect.addAttribute("id", id);
        return "redirect:/bookings/{id}/notes";
    }

    private BookingFilter withArchiveDefault(BookingFilter filter) {
        return new BookingFilter(filter.q(), filter.employeeId(), filter.status(), filter.guest(), filter.urgency(),
                filter.tag(), Boolean.TRUE.equals(filter.archived()), filter.spot(), filter.spotOp(), filter.from(), filter.to());
    }
}
