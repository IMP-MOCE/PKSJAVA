package ru.pks.model;

// статусы брони и правила, как между ними можно переходить
// схема такая: CREATED -> CONFIRMED -> COMPLETED, и из первых двух можно уйти в CANCELLED
// COMPLETED и CANCELLED конечные, оттуда уже никуда
public enum BookingStatus {
    CREATED, CONFIRMED, COMPLETED, CANCELLED;

    // активная бронь реально занимает место на эту дату, закрытые (завершённые и отменённые) место не держат
    public boolean active() {
        return this == CREATED || this == CONFIRMED;
    }

    // можно ли из текущего статуса перейти в nextStatus
    // переход в тот же самый статус всегда ок, дальше switch расписывает разрешённые переходы
    public boolean canChangeTo(BookingStatus nextStatus) {
        return this == nextStatus || switch (this) {
            case CREATED -> nextStatus == CONFIRMED || nextStatus == CANCELLED;
            case CONFIRMED -> nextStatus == COMPLETED || nextStatus == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
