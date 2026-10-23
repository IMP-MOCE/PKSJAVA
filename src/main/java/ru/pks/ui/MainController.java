package ru.pks.ui;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import ru.pks.model.Booking;
import ru.pks.model.BookingStatus;
import ru.pks.model.Employee;
import ru.pks.service.ParkingService;
import ru.pks.util.ExcelExporter;

// контроллер главного окна (разметка в main.fxml): три вкладки, бронирования, сотрудники и статистика
// методы с @FXML вызываются по кнопкам, в fxml это записано как onAction="#имяМетода"
// вся логика лежит в сервисе, а тут только забрать данные из полей, вызвать сервис и обновить таблицы
public final class MainController {
    private final ParkingService service;
    // ObservableList это список, за которым следит таблица: поменяли список, и таблица перерисовалась сама
    private final ObservableList<Booking> shownBookings = FXCollections.observableArrayList();
    private final ObservableList<Employee> shownEmployees = FXCollections.observableArrayList();
    // в брони лежит только id сотрудника, а по этой мапе быстро находим его имя и почту
    private final Map<Long, Employee> employeesById = new HashMap<>();

    // элементы из main.fxml, javafx подставляет их сам по совпадению имени поля и fx:id
    @FXML private TableView<Booking> bookingTable;
    @FXML private TableColumn<Booking, Long> bookingIdColumn;
    @FXML private TableColumn<Booking, String> bookingEmployeeColumn, bookingStatusColumn;
    @FXML private TableColumn<Booking, Integer> bookingSpotColumn;
    @FXML private TableColumn<Booking, LocalDate> bookingDateColumn;
    @FXML private TableView<Employee> employeeTable;
    @FXML private TableColumn<Employee, String> employeeNameColumn, employeeEmailColumn;
    @FXML private TextField searchField;
    // statusFilterComboBox это фильтр над таблицей, а newStatusComboBox это выбор нового статуса для выделенной брони
    @FXML private ComboBox<BookingStatus> statusFilterComboBox, newStatusComboBox;
    @FXML private DatePicker fromDatePicker, toDatePicker;
    // bookingsInfoLabel это строчка под таблицей: там пишем сколько броней показано или куда сохранили excel
    @FXML private Label bookingsInfoLabel, statisticsLabel, selectedEmployeeLabel;
    @FXML private Button editButton, deleteButton, statusButton;

    public MainController(ParkingService service) { this.service = service; }

    // javafx сам вызывает его после загрузки fxml, тут один раз настраиваем таблицы, списки и кнопки
    @FXML private void initialize() {
        // для каждой колонки говорим, какое значение брать из строки
        column(bookingIdColumn, Booking::id);
        column(bookingEmployeeColumn, booking -> employeesById.containsKey(booking.employeeId())
            ? employeesById.get(booking.employeeId()).name() : "Сотрудник удалён");
        column(bookingSpotColumn, Booking::spot);
        column(bookingDateColumn, Booking::date);
        column(bookingStatusColumn, booking -> FxDialogs.status(booking.status()));
        column(employeeNameColumn, Employee::name);
        column(employeeEmailColumn, Employee::email);
        bookingTable.setItems(shownBookings);
        employeeTable.setItems(shownEmployees);
        bookingTable.setPlaceholder(new Label("Бронирований нет. Измените фильтры или добавьте бронь."));
        employeeTable.setPlaceholder(new Label("Добавьте первого сотрудника."));
        FxDialogs.statuses(statusFilterComboBox, true);
        FxDialogs.statuses(newStatusComboBox, false);
        statusFilterComboBox.getSelectionModel().selectFirst();
        FxDialogs.dateFormat(fromDatePicker);
        FxDialogs.dateFormat(toDatePicker);
        // биндинг: кнопки сами выключаются, пока в таблице ничего не выделено, и включаются обратно, когда выделили
        var nothingSelected = bookingTable.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(nothingSelected);
        deleteButton.disableProperty().bind(nothingSelected);
        statusButton.disableProperty().bind(nothingSelected);
        // слушатель: при каждой смене выделенной строки показываем сотрудника этой брони и её текущий статус
        bookingTable.getSelectionModel().selectedItemProperty().addListener((property, previousBooking, selectedBooking) -> {
            Employee selectedEmployee = selectedBooking == null ? null : employeesById.get(selectedBooking.employeeId());
            selectedEmployeeLabel.setText(selectedEmployee == null
                ? "Выберите бронь для просмотра сотрудника."
                : selectedEmployee.name() + " · " + selectedEmployee.email());
            newStatusComboBox.setValue(selectedBooking == null ? null : selectedBooking.status());
        });
    }

    // настраивает колонку: valueGetter говорит, что показывать в ячейке для каждой строки таблицы
    // ReadOnlyObjectWrapper просто заворачивает значение в обёртку, которую ждёт таблица
    private static <S, T> void column(TableColumn<S, T> column, Function<S, T> valueGetter) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cell.getValue())));
    }

    // перечитывает всё из базы с учётом фильтров и обновляет все три вкладки
    // висит на кнопках "Найти" и "Обновить" и на enter в поле поиска
    @FXML public void refresh() {
        execute(() -> {
            // сначала читаем всё нужное, и только потом трогаем экран
            // если чтение упадёт, на экране останутся старые данные, а не половина новых
            var foundBookings = service.search(searchField.getText(), statusFilterComboBox.getValue(),
                FxDialogs.date(fromDatePicker, false), FxDialogs.date(toDatePicker, false));
            var allEmployees = service.employees();
            var statisticsByLabel = service.statistics();

            employeesById.clear();
            allEmployees.forEach(employee -> employeesById.put(employee.id(), employee));
            shownEmployees.setAll(allEmployees);
            shownBookings.setAll(foundBookings);
            // после замены данных заново применяем сортировку, которую пользователь выбрал кликом по заголовку
            bookingTable.sort();
            employeeTable.sort();
            bookingsInfoLabel.setText("Показано бронирований: " + shownBookings.size());

            // собираем текст статистики построчно, ключи-статусы (CREATED и т.д.) переводим на русский
            StringBuilder statisticsText = new StringBuilder();
            statisticsByLabel.forEach((label, value) -> {
                String shownLabel = label;
                for (BookingStatus status : BookingStatus.values())
                    if (label.equals(status.name())) shownLabel = FxDialogs.status(status);
                statisticsText.append(shownLabel).append(": ").append(value).append('\n');
            });
            statisticsLabel.setText(statisticsText.toString());
        });
    }

    // кнопка "Сбросить": чистим все фильтры и перечитываем данные
    @FXML private void resetFilters() {
        searchField.clear();
        statusFilterComboBox.getSelectionModel().selectFirst();
        // у поля даты чистим и значение, и набранный текст, иначе date() прочитает старый текст
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        fromDatePicker.getEditor().clear();
        toDatePicker.getEditor().clear();
        refresh();
    }

    @FXML private void addBooking() { bookingForm(null); }
    @FXML private void editBooking() { execute(() -> bookingForm(selected())); }

    // открывает форму брони: bookingToEdit = null значит новая, иначе изменение
    // после сохранения сбрасываем фильтры, чтобы новая или изменённая бронь точно была видна в таблице
    private void bookingForm(Booking bookingToEdit) {
        execute(() -> {
            var formController = new BookingFormController(service, service.employees(), bookingToEdit);
            String formTitle = bookingToEdit == null ? "Новое бронирование" : "Изменить бронирование";
            if (FxDialogs.form(owner(), formTitle, "booking-form.fxml", formController, formController::save)) resetFilters();
        });
    }

    @FXML private void addEmployee() {
        execute(() -> {
            var formController = new EmployeeFormController(service);
            if (FxDialogs.form(owner(), "Новый сотрудник", "employee-form.fxml", formController, formController::save)) refresh();
        });
    }

    // удаление выделенной брони, но сначала переспрашиваем да/нет
    @FXML private void deleteBooking() {
        execute(() -> {
            Booking bookingToDelete = selected();
            var confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Удалить бронь №" + bookingToDelete.id() + " на место " + bookingToDelete.spot() + "?", ButtonType.YES, ButtonType.NO);
            confirmation.initOwner(owner());
            confirmation.setTitle("Удаление бронирования");
            confirmation.setHeaderText("Это действие удалит запись из базы.");
            if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                service.delete(bookingToDelete.id());
                refresh();
            }
        });
    }

    // кнопка "Применить" возле выбора статуса
    @FXML private void changeStatus() {
        execute(() -> { service.changeStatus(selected().id(), newStatusComboBox.getValue()); refresh(); });
    }

    // спрашиваем, куда сохранить, и выгружаем в excel
    @FXML private void export() {
        execute(() -> {
            var fileChooser = new FileChooser();
            fileChooser.setTitle("Сохранить текущий список бронирований");
            fileChooser.setInitialFileName("parking.xlsx");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
            var chosenFile = fileChooser.showSaveDialog(owner());
            // null значит, что в окне выбора файла нажали отмену
            if (chosenFile == null) return;
            try {
                // выгружаем ровно то, что сейчас видно в таблице, с теми же фильтрами и в том же порядке
                ExcelExporter.export(chosenFile.toPath(), List.copyOf(shownEmployees), List.copyOf(shownBookings));
                bookingsInfoLabel.setText("Сохранено: " + chosenFile.getAbsolutePath());
            } catch (IOException e) { FxDialogs.error(owner(), e); }
        });
    }

    // выделенная в таблице бронь, а если ничего не выделено, ошибка
    private Booking selected() {
        Booking selected = bookingTable.getSelectionModel().getSelectedItem();
        if (selected == null) throw new IllegalArgumentException("Выберите бронирование в таблице.");
        return selected;
    }

    // окно, в котором лежит таблица, нужно чтобы диалоги открывались поверх него
    private Window owner() { return bookingTable.getScene().getWindow(); }

    // запускает действие и, если оно упало, показывает ошибку в окошке, а не роняет программу
    // так не нужно писать try/catch в каждом обработчике кнопки
    private void execute(Runnable action) {
        try { action.run(); }
        catch (RuntimeException e) { FxDialogs.error(owner(), e); }
    }
}
