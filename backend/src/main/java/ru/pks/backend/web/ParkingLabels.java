package ru.pks.backend.web;

import java.util.Map;

public final class ParkingLabels {
    private ParkingLabels() { }

    public static final Map<String, String> STATUS = Map.of(
            "CREATED", "Создана", "CONFIRMED", "Подтверждена", "COMPLETED", "Завершена", "CANCELLED", "Отменена");
    public static final Map<String, String> URGENCY = Map.of(
            "NORMAL", "Обычная", "SOON", "Скоро", "OVERDUE", "Просрочена");
    public static final Map<String, String> ROLE = Map.of(
            "VIEWER", "Просмотр", "EDITOR", "Редактор", "ADMIN", "Администратор");
}
