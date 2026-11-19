package ru.pks.backend.filter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.pks.backend.domain.Booking;
import ru.pks.backend.domain.BookingStatus;
import ru.pks.backend.error.BadRequestException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;

public final class BookingSpecifications {
    private BookingSpecifications() { }

    public static Specification<Booking> from(BookingFilter filter, LocalDate today) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to()))
            throw new BadRequestException("Начало периода позже конца");
        if (filter.spot() != null && (filter.spot() < 1 || filter.spot() > 50))
            throw new BadRequestException("Место должно быть от 1 до 50");
        String operation = filter.spotOp() == null ? "eq" : filter.spotOp();
        if (!java.util.Set.of("eq", "gt", "gte", "lt", "lte").contains(operation))
            throw new BadRequestException("Операция сравнения: eq, gt, gte, lt или lte");
        return (root, query, cb) -> {
            var conditions = new ArrayList<Predicate>();
            if (filter.q() != null && !filter.q().isBlank()) {
                String text = filter.q().strip().toLowerCase(Locale.ROOT);
                String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                conditions.add(cb.or(cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("purpose")), pattern, '\\'),
                        cb.like(cb.lower(root.get("employee").get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("employee").get("email")), pattern, '\\')));
            }
            if (filter.employeeId() != null)
                conditions.add(cb.equal(root.get("employee").get("uuid"), filter.employeeId()));
            if (filter.status() != null) conditions.add(cb.equal(root.get("status"), filter.status()));
            if (filter.guest() != null) conditions.add(cb.equal(root.get("guest"), filter.guest()));
            if (filter.archived() != null) conditions.add(cb.equal(root.get("archived"), filter.archived()));
            if (filter.tag() != null && !filter.tag().isBlank()) {
                conditions.add(cb.isMember(filter.tag().strip().toLowerCase(Locale.ROOT), root.get("tags")));
            }
            if (filter.from() != null) conditions.add(cb.greaterThanOrEqualTo(root.get("date"), filter.from()));
            if (filter.to() != null) conditions.add(cb.lessThanOrEqualTo(root.get("date"), filter.to()));
            if (filter.spot() != null) {
                var spot = root.<Integer>get("spot");
                conditions.add(switch (operation) {
                    case "gt" -> cb.greaterThan(spot, filter.spot());
                    case "gte" -> cb.greaterThanOrEqualTo(spot, filter.spot());
                    case "lt" -> cb.lessThan(spot, filter.spot());
                    case "lte" -> cb.lessThanOrEqualTo(spot, filter.spot());
                    default -> cb.equal(spot, filter.spot());
                });
            }
            if (filter.urgency() != null) {
                // условия совпадают с правилами калькулятора и работают в sql до пагинации
                Predicate overdue = cb.and(cb.isFalse(root.get("archived")),
                        cb.equal(root.get("status"), BookingStatus.CREATED), cb.lessThan(root.get("date"), today));
                Predicate soon = cb.and(cb.isFalse(root.get("archived")),
                        cb.equal(root.get("status"), BookingStatus.CREATED),
                        cb.between(root.get("date"), today, today.plusDays(1)));
                conditions.add(switch (filter.urgency()) {
                    case OVERDUE -> overdue;
                    case SOON -> soon;
                    case NORMAL -> cb.not(cb.or(overdue, soon));
                });
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
