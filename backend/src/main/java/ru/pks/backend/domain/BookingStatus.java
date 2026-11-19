package ru.pks.backend.domain;

public enum BookingStatus {
    CREATED, CONFIRMED, COMPLETED, CANCELLED;

    public boolean active() {
        return this == CREATED || this == CONFIRMED;
    }

    public boolean canChangeTo(BookingStatus next) {
        if (this == next) return true;
        return switch (this) {
            case CREATED -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
