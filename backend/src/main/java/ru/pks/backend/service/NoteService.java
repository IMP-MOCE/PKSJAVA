package ru.pks.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pks.backend.domain.BookingNote;
import ru.pks.backend.domain.Role;
import ru.pks.backend.dto.NoteDtos;
import ru.pks.backend.error.ConflictException;
import ru.pks.backend.error.NotFoundException;
import ru.pks.backend.repository.BookingNoteRepository;
import ru.pks.backend.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NoteService {
    private final BookingNoteRepository notes;
    private final BookingService bookings;
    private final CurrentUserService user;
    private final AuditService audit;

    public NoteService(BookingNoteRepository notes, BookingService bookings, CurrentUserService user, AuditService audit) {
        this.notes = notes;
        this.bookings = bookings;
        this.user = user;
        this.audit = audit;
    }

    public List<NoteDtos.Response> list(UUID bookingId) {
        bookings.require(bookingId);
        return notes.findByBookingUuidOrderByPinnedDescCreatedAtDesc(bookingId).stream().map(this::response).toList();
    }

    @Transactional
    public NoteDtos.Response save(UUID bookingId, UUID noteId, NoteDtos.SaveRequest request) {
        BookingNote note;
        if (noteId == null) {
            note = new BookingNote();
            note.setBooking(bookings.require(bookingId));
            note.setAuthor(user.requireEntity());
        } else {
            note = require(bookingId, noteId);
            checkAuthor(note);
            if (note.getVersion() != request.version()) throw new ConflictException("Заметка уже изменена");
        }
        note.setBody(request.body().strip());
        note.setPinned(request.pinned());
        notes.saveAndFlush(note);
        audit.record("NOTE_SAVED", "BOOKING", bookingId, "Сохранена заметка", Map.of("noteId", note.getId()));
        return response(note);
    }

    @Transactional
    public void delete(UUID bookingId, UUID noteId) {
        BookingNote note = require(bookingId, noteId);
        checkAuthor(note);
        notes.delete(note);
        audit.record("NOTE_DELETED", "BOOKING", bookingId, "Удалена заметка", Map.of("noteId", noteId));
    }

    private BookingNote require(UUID bookingId, UUID noteId) {
        return notes.findById(noteId).filter(note -> note.getBooking().getUuid().equals(bookingId))
                .orElseThrow(() -> new NotFoundException("Заметка не найдена"));
    }

    private void checkAuthor(BookingNote note) {
        var actor = user.requireEntity();
        if (!actor.getId().equals(note.getAuthor().getId()) && !actor.getRoles().contains(Role.ADMIN))
            throw new AccessDeniedException("Изменять заметку может автор или администратор");
    }

    private NoteDtos.Response response(BookingNote note) {
        return new NoteDtos.Response(note.getId(), note.getAuthor().getId(), note.getAuthor().getDisplayName(),
                note.getBody(), note.isPinned(), note.getCreatedAt(), note.getUpdatedAt(), note.getVersion());
    }
}
