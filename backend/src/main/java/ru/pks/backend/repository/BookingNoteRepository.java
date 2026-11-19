package ru.pks.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.pks.backend.domain.BookingNote;
import java.util.List;
import java.util.UUID;

public interface BookingNoteRepository extends JpaRepository<BookingNote, UUID> {
    List<BookingNote> findByBookingUuidOrderByPinnedDescCreatedAtDesc(UUID bookingId);
    void deleteByBookingUuid(UUID bookingId);
}
