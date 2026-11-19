package ru.pks.backend.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.pks.backend.dto.NoteDtos;
import ru.pks.backend.service.NoteService;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings/{bookingId}/notes")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class NoteController {
    private final NoteService notes;

    public NoteController(NoteService notes) { this.notes = notes; }

    @GetMapping
    public List<NoteDtos.Response> list(@PathVariable UUID bookingId) { return notes.list(bookingId); }

    @PostMapping
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public NoteDtos.Response create(@PathVariable UUID bookingId, @Valid @RequestBody NoteDtos.SaveRequest request) {
        return notes.save(bookingId, null, request);
    }

    @PutMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public NoteDtos.Response update(@PathVariable UUID bookingId, @PathVariable UUID noteId,
            @Valid @RequestBody NoteDtos.SaveRequest request) {
        return notes.save(bookingId, noteId, request);
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public void delete(@PathVariable UUID bookingId, @PathVariable UUID noteId) { notes.delete(bookingId, noteId); }
}
