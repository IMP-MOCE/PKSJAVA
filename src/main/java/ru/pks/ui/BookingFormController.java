package ru.pks.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import ru.pks.model.*;
import ru.pks.service.ParkingService;
import java.time.LocalDate;
import java.util.List;

final class BookingFormController {
    private final ParkingService service;
    private final List<Employee> employees;
    private final Booking booking;
    @FXML private ComboBox<Employee> employee;
    @FXML private TextField spot;
    @FXML private DatePicker date;

    BookingFormController(ParkingService service, List<Employee> employees, Booking booking) {
        this.service = service;
        this.employees = employees;
        this.booking = booking;
    }

    @FXML private void initialize() {
        employee.getItems().setAll(employees);
        employee.setConverter(new StringConverter<>() {
            public String toString(Employee e) { return e == null ? "" : e.name() + " — " + e.email(); }
            public Employee fromString(String text) { throw new UnsupportedOperationException(); }
        });
        FxDialogs.dateFormat(date);
        date.setValue(booking == null ? LocalDate.now() : booking.date());
        if (booking != null) {
            employee.setValue(employees.stream().filter(e -> e.id() == booking.employeeId()).findFirst().orElse(null));
            employee.setDisable(true);
            spot.setText(Integer.toString(booking.spot()));
        }
    }

    void save() {
        if (employee.getValue() == null) throw new IllegalArgumentException("Выберите сотрудника из списка.");
        int number;
        try { number = Integer.parseInt(spot.getText().strip()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Место: введите целое число от 1 до 50."); }
        LocalDate day = FxDialogs.date(date, true);
        if (booking == null) service.create(employee.getValue().id(), number, day);
        else service.update(booking.id(), number, day);
    }
}
