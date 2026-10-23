package ru.pks.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import ru.pks.model.*;
import ru.pks.service.ParkingService;
import java.time.LocalDate;
import java.util.List;

// контроллер формы брони (разметка в booking-form.fxml), одна форма и на создание, и на изменение
// если bookingToEdit = null, создаём новую бронь, иначе редактируем эту
// сам диалог с кнопками собирает FxDialogs.form, а этот класс только заполняет поля и сохраняет
final class BookingFormController {
    private final ParkingService service;
    private final List<Employee> employees;
    private final Booking bookingToEdit;
    // @FXML поля javafx заполняет сам при загрузке fxml, имя поля должно совпадать с fx:id в разметке
    @FXML private ComboBox<Employee> employeeComboBox;
    @FXML private TextField spotField;
    @FXML private DatePicker datePicker;

    BookingFormController(ParkingService service, List<Employee> employees, Booking bookingToEdit) {
        this.service = service;
        this.employees = employees;
        this.bookingToEdit = bookingToEdit;
    }

    // javafx сам вызывает этот метод после загрузки fxml, когда @FXML поля уже заполнены
    @FXML private void initialize() {
        employeeComboBox.getItems().setAll(employees);
        // объясняем выпадающему списку, как показывать сотрудника текстом: "имя — почта"
        // fromString не нужен, потому что руками в список ничего не вводят
        employeeComboBox.setConverter(new StringConverter<>() {
            public String toString(Employee employee) { return employee == null ? "" : employee.name() + " — " + employee.email(); }
            public Employee fromString(String text) { throw new UnsupportedOperationException(); }
        });
        FxDialogs.dateFormat(datePicker);
        boolean isNewBooking = bookingToEdit == null;
        datePicker.setValue(isNewBooking ? LocalDate.now() : bookingToEdit.date());
        if (!isNewBooking) {
            // при редактировании подставляем текущие значения, а сотрудника менять нельзя, поэтому блокируем список
            employeeComboBox.setValue(employees.stream()
                .filter(employee -> employee.id() == bookingToEdit.employeeId()).findFirst().orElse(null));
            employeeComboBox.setDisable(true);
            spotField.setText(Integer.toString(bookingToEdit.spot()));
        }
    }

    // вызывается по кнопке "Сохранить", при любой ошибке кидает исключение с текстом,
    // FxDialogs его ловит и показывает прямо в форме
    void save() {
        if (employeeComboBox.getValue() == null) throw new IllegalArgumentException("Выберите сотрудника из списка.");
        int spotNumber;
        try { spotNumber = Integer.parseInt(spotField.getText().strip()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Место: введите целое число от 1 до 50."); }
        LocalDate bookingDate = FxDialogs.date(datePicker, true);
        if (bookingToEdit == null) service.create(employeeComboBox.getValue().id(), spotNumber, bookingDate);
        else service.update(bookingToEdit.id(), spotNumber, bookingDate);
    }
}
