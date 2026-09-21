package ru.pks.model;

public enum BookingStatus {
    CREATED, CONFIRMED, COMPLETED, CANCELLED;

    public boolean active() {
        return this == CREATED || this == CONFIRMED;
    }

    public boolean canChangeTo(BookingStatus next) {
        return this == next || switch (this) {
            case CREATED -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
