package ru.pks.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import ru.pks.model.*;
import ru.pks.service.ParkingService;
import ru.pks.util.ExcelExporter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

public final class MainController {
    private final ParkingService service;
    private final ObservableList<Booking> bookings = FXCollections.observableArrayList();
    private final ObservableList<Employee> employees = FXCollections.observableArrayList();
    private final Map<Long, Employee> people = new HashMap<>();
    @FXML private TableView<Booking> bookingTable;
    @FXML private TableColumn<Booking, Long> bookingId;
    @FXML private TableColumn<Booking, String> employeeName, bookingStatus;
    @FXML private TableColumn<Booking, Integer> bookingSpot;
    @FXML private TableColumn<Booking, LocalDate> bookingDate;
    @FXML private TableView<Employee> employeeTable;
    @FXML private TableColumn<Employee, String> personName, personEmail;
    @FXML private TextField search;
    @FXML private ComboBox<BookingStatus> statusFilter, newStatus;
    @FXML private DatePicker fromDate, toDate;
    @FXML private Label count, statistics, selectedEmployee;
    @FXML private Button editButton, deleteButton, statusButton;

    public MainController(ParkingService service) { this.service = service; }

    @FXML private void initialize() {
        column(bookingId, Booking::id);
        column(employeeName, b -> people.containsKey(b.employeeId()) ? people.get(b.employeeId()).name() : "Сотрудник удалён");
        column(bookingSpot, Booking::spot);
        column(bookingDate, Booking::date);
        column(bookingStatus, b -> FxDialogs.status(b.status()));
        column(personName, Employee::name);
        column(personEmail, Employee::email);
        bookingTable.setItems(bookings);
        employeeTable.setItems(employees);
        bookingTable.setPlaceholder(new Label("Бронирований нет. Измените фильтры или добавьте бронь."));
        employeeTable.setPlaceholder(new Label("Добавьте первого сотрудника."));
        FxDialogs.statuses(statusFilter, true);
        FxDialogs.statuses(newStatus, false);
        statusFilter.getSelectionModel().selectFirst();
        FxDialogs.dateFormat(fromDate);
        FxDialogs.dateFormat(toDate);
        var noSelection = bookingTable.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        deleteButton.disableProperty().bind(noSelection);
        statusButton.disableProperty().bind(noSelection);
        bookingTable.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> {
            Employee person = selected == null ? null : people.get(selected.employeeId());
            selectedEmployee.setText(person == null ? "Выберите бронь для просмотра сотрудника." : person.name() + " · " + person.email());
            newStatus.setValue(selected == null ? null : selected.status());
        });
    }

    private static <S, T> void column(TableColumn<S, T> column, Function<S, T> value) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
    }

    @FXML public void refresh() {
        execute(() -> {
            // Сначала читаем всё; при ошибке старые данные остаются на экране.
            var found = service.search(search.getText(), statusFilter.getValue(),
                FxDialogs.date(fromDate, false), FxDialogs.date(toDate, false));
            var staff = service.employees();
            var numbers = service.statistics();
            people.clear();
            staff.forEach(e -> people.put(e.id(), e));
            employees.setAll(staff);
            bookings.setAll(found);
            bookingTable.sort();
            employeeTable.sort();
            count.setText("Показано бронирований: " + bookings.size());
            StringBuilder text = new StringBuilder();
            numbers.forEach((key, value) -> {
                String label = key;
                for (BookingStatus status : BookingStatus.values())
                    if (key.equals(status.name())) label = FxDialogs.status(status);
                text.append(label).append(": ").append(value).append('\n');
            });
            statistics.setText(text.toString());
        });
    }

    @FXML private void resetFilters() {
        search.clear();
        statusFilter.getSelectionModel().selectFirst();
        fromDate.setValue(null);
        toDate.setValue(null);
        fromDate.getEditor().clear();
        toDate.getEditor().clear();
        refresh();
    }

    @FXML private void addBooking() { bookingForm(null); }
    @FXML private void editBooking() { execute(() -> bookingForm(selected())); }

    private void bookingForm(Booking booking) {
        execute(() -> {
            var controller = new BookingFormController(service, service.employees(), booking);
            if (FxDialogs.form(owner(), booking == null ? "Новое бронирование" : "Изменить бронирование",
                "booking-form.fxml", controller, controller::save)) resetFilters();
        });
    }

    @FXML private void addEmployee() {
        execute(() -> {
            var controller = new EmployeeFormController(service);
            if (FxDialogs.form(owner(), "Новый сотрудник", "employee-form.fxml", controller, controller::save)) refresh();
        });
    }

    @FXML private void deleteBooking() {
        execute(() -> {
            Booking booking = selected();
            var alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить бронь №" + booking.id() + " на место " + booking.spot() + "?", ButtonType.YES, ButtonType.NO);
            alert.initOwner(owner());
            alert.setTitle("Удаление бронирования");
            alert.setHeaderText("Это действие удалит запись из базы.");
            if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                service.delete(booking.id());
                refresh();
            }
        });
    }

    @FXML private void changeStatus() {
        execute(() -> { service.changeStatus(selected().id(), newStatus.getValue()); refresh(); });
    }

    @FXML private void export() {
        execute(() -> {
            var chooser = new FileChooser();
            chooser.setTitle("Сохранить текущий список бронирований");
            chooser.setInitialFileName("parking.xlsx");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
            var file = chooser.showSaveDialog(owner());
            if (file == null) return;
            try {
                // Экспортируется показанный список с текущими фильтрами и порядком строк.
                ExcelExporter.export(file.toPath(), List.copyOf(employees), List.copyOf(bookings));
                count.setText("Сохранено: " + file.getAbsolutePath());
            } catch (IOException e) { FxDialogs.error(owner(), e); }
        });
    }

    private Booking selected() {
        Booking booking = bookingTable.getSelectionModel().getSelectedItem();
        if (booking == null) throw new IllegalArgumentException("Выберите бронирование в таблице.");
        return booking;
    }

    private Window owner() { return bookingTable.getScene().getWindow(); }

    private void execute(Runnable action) {
        try { action.run(); }
        catch (RuntimeException e) { FxDialogs.error(owner(), e); }
    }
}
