package ru.pks.backend.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking extends AuditedVersionedEntity {
    // числовой id сохраняет совместимость с настольным приложением
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private UUID uuid = UUID.randomUUID();
    @Column(nullable = false, unique = true, length = 100)
    private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    @Column(nullable = false)
    private int spot;
    @Column(name = "booking_date", nullable = false)
    private LocalDate date;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CREATED;
    @Column(nullable = false, length = 200)
    private String purpose = "Рабочий день";
    @Column(nullable = false)
    private boolean guest;
    @Column(nullable = false)
    private boolean archived;
    @ElementCollection
    @CollectionTable(name = "booking_tags", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "tag", nullable = false, length = 50)
    private Set<String> tags = new LinkedHashSet<>();

    public Long getId() { return id; }
    public UUID getUuid() { return uuid; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public int getSpot() { return spot; }
    public void setSpot(int spot) { this.spot = spot; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public boolean isGuest() { return guest; }
    public void setGuest(boolean guest) { this.guest = guest; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = new LinkedHashSet<>(tags); }
}
