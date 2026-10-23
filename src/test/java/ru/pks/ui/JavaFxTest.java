package ru.pks.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ru.pks.model.*;
import ru.pks.repository.ParkingRepository;
import ru.pks.service.ParkingService;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

// тест настоящего окна: грузим main.fxml, жмём кнопки, заполняем формы и смотрим, что поменялось
// база не нужна, сервис работает поверх хранилища в памяти (внизу файла)
// запускается только если RUN_FX_TESTS=true, потому что реально открывает окна на экране
@EnabledIfEnvironmentVariable(named = "RUN_FX_TESTS", matches = "true")
class JavaFxTest {
    // один раз на все тесты поднимаем javafx и ждём, пока он стартанёт
    @BeforeAll static void startJavaFx() throws Exception {
        var javaFxStarted = new CountDownLatch(1);
        // setImplicitExit(false), чтобы javafx не выключился сам, когда мы закроем последнее окно
        Platform.startup(() -> { Platform.setImplicitExit(false); javaFxStarted.countDown(); });
        assertTrue(javaFxStarted.await(15, TimeUnit.SECONDS));
    }

    @AfterAll static void stopJavaFx() { Platform.exit(); }

    @Test void fxmlFormsTableFiltersAndDeleteConfirmation() throws Exception {
        // с окнами javafx можно работать только из его собственного потока,
        // поэтому весь тест заворачиваем в задачу и отдаём её туда через Platform.runLater
        var uiTask = new FutureTask<Void>(() -> {
            var memoryRepository = new MemoryRepository();
            var service = new ParkingService(memoryRepository);
            // Петров на место 24 сегодня, Смирнова на место 12 завтра
            var petrovBooking = service.create(1, 24, LocalDate.now());
            service.create(2, 12, LocalDate.now().plusDays(1));
            var controller = new MainController(service);
            var loader = new FXMLLoader(MainController.class.getResource("main.fxml"));
            loader.setController(controller);
            Parent root = loader.load();
            var stage = new Stage();
            var scene = new Scene(root, 1080, 700);
            scene.getStylesheets().add(MainController.class.getResource("parking.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            try {
                // --- таблица и поиск ---
                controller.refresh();
                TableView<Booking> bookingTable = node(root, "bookingTable");
                assertEquals(2, bookingTable.getItems().size());
                TextField searchField = node(root, "searchField");
                searchField.setText("24");
                controller.refresh();
                assertEquals(List.of(petrovBooking), List.copyOf(bookingTable.getItems()));
                searchField.clear();
                controller.refresh();
                // сортируем по колонке "Место" (она третья по счёту), первым должно встать место 12
                TableColumn<Booking, Integer> spotColumn = (TableColumn<Booking, Integer>) bookingTable.getColumns().get(2);
                bookingTable.getSortOrder().setAll(spotColumn);
                assertEquals(12, bookingTable.getItems().getFirst().spot());
                // выделили бронь, и под таблицей появилась почта сотрудника
                bookingTable.getSelectionModel().select(petrovBooking);
                assertTrue(((Label) root.lookup("#selectedEmployeeLabel")).getText().contains("petrov@company.ru"));

                // --- скриншоты окна в target, в обычном и минимальном размере, чтобы глазами проверить вёрстку ---
                snapshot(scene, "target/javafx-main.png");
                // root нельзя сразу повесить на вторую сцену, поэтому сначала отцепляем его от первой
                scene.setRoot(new javafx.scene.layout.VBox());
                var smallScene = new Scene(root, 850, 580);
                smallScene.getStylesheets().addAll(scene.getStylesheets());
                stage.setScene(smallScene);
                stage.sizeToScene();
                snapshot(smallScene, "target/javafx-small.png");

                // --- создание брони через форму ---
                // хитрость: кнопка "Добавить бронь" открывает модальный диалог через showAndWait и ждёт, пока его закроют
                // поэтому действия внутри диалога заранее ставим в очередь через runLater, они выполнятся, когда диалог уже открыт
                // жмём настоящую кнопку "Сохранить": с кривым местом диалог не закрывается и ввод не теряется
                Platform.runLater(() -> {
                    Parent dialog = dialogRoot();
                    TextField spotField = node(dialog, "spotField");
                    ComboBox<Employee> employeeComboBox = node(dialog, "employeeComboBox");
                    employeeComboBox.getSelectionModel().selectFirst();
                    DatePicker datePicker = node(dialog, "datePicker");
                    datePicker.setValue(LocalDate.now().plusDays(2));
                    spotField.setText("abc");
                    saveButton(dialog).fire();
                    assertEquals("abc", spotField.getText());
                    assertTrue(((Label) dialog.lookup(".error")).getText().contains("целое"));
                    spotField.setText("30");
                    saveButton(dialog).fire();
                });
                ((Button) root.lookup("#addBookingButton")).fire();
                assertEquals(3, bookingTable.getItems().size());

                // --- изменение брони: в форме уже стоит старое место, а сотрудник заблокирован ---
                bookingTable.getSelectionModel().select(petrovBooking);
                Platform.runLater(() -> {
                    Parent dialog = dialogRoot();
                    TextField spotField = node(dialog, "spotField");
                    assertEquals("24", spotField.getText());
                    assertTrue(((ComboBox<?>) dialog.lookup("#employeeComboBox")).isDisabled());
                    spotField.setText("26");
                    saveButton(dialog).fire();
                });
                ((Button) root.lookup("#editButton")).fire();
                Booking editedBooking = service.get(petrovBooking.id());
                assertEquals(26, editedBooking.spot());

                // --- смена статуса ---
                bookingTable.getSelectionModel().select(editedBooking);
                ComboBox<BookingStatus> newStatusComboBox = node(root, "newStatusComboBox");
                newStatusComboBox.setValue(BookingStatus.CONFIRMED);
                ((Button) root.lookup("#statusButton")).fire();
                assertEquals(BookingStatus.CONFIRMED, service.get(petrovBooking.id()).status());

                // --- удаление: на "Нет" ничего не удаляется, на "Да" удаляется ---
                bookingTable.getSelectionModel().select(service.get(petrovBooking.id()));
                Platform.runLater(() -> ((Button) ((DialogPane) dialogRoot()).lookupButton(ButtonType.NO)).fire());
                ((Button) root.lookup("#deleteButton")).fire();
                assertEquals(3, bookingTable.getItems().size());
                bookingTable.getSelectionModel().select(service.get(petrovBooking.id()));
                Platform.runLater(() -> ((Button) ((DialogPane) dialogRoot()).lookupButton(ButtonType.YES)).fire());
                ((Button) root.lookup("#deleteButton")).fire();
                assertEquals(2, bookingTable.getItems().size());

                // --- форма сотрудника, тут без диалога: грузим fxml сами и вызываем save напрямую ---
                var employeeFormController = new EmployeeFormController(service);
                var employeeFormLoader = new FXMLLoader(MainController.class.getResource("employee-form.fxml"));
                employeeFormLoader.setController(employeeFormController);
                Parent employeeFormRoot = employeeFormLoader.load();
                ((TextField) employeeFormRoot.lookup("#nameField")).setText("Мария");
                ((TextField) employeeFormRoot.lookup("#emailField")).setText("maria@company.ru");
                employeeFormController.save();
                assertEquals(3, service.employees().size());
            } finally { stage.close(); }
            return null;
        });
        Platform.runLater(uiTask);
        // ждём, пока задача отработает в потоке javafx, а если внутри упал assert, get() пробросит ошибку сюда
        uiTask.get(45, TimeUnit.SECONDS);
    }

    // ищет элемент по fx:id и сразу приводит к нужному типу, чтобы не писать каст каждый раз
    @SuppressWarnings("unchecked")
    private static <T> T node(Parent root, String fxId) { return (T) root.lookup("#" + fxId); }

    // корень самого последнего открытого окна, то есть текущего диалога
    private static Parent dialogRoot() {
        return Window.getWindows().stream().filter(Window::isShowing)
            .reduce((previousWindow, nextWindow) -> nextWindow).orElseThrow().getScene().getRoot();
    }

    // кнопка "Сохранить" в диалоге, ищем её по типу OK_DONE
    private static Button saveButton(Parent dialogRoot) {
        var dialogPane = (DialogPane) dialogRoot;
        return (Button) dialogPane.lookupButton(dialogPane.getButtonTypes().stream()
            .filter(buttonType -> buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow());
    }

    // делает скриншот сцены и сохраняет в png, пиксели копируем по одному из картинки javafx в обычную java-картинку
    private static void snapshot(Scene scene, String filePath) throws Exception {
        var fxImage = scene.snapshot(null);
        var pngImage = new BufferedImage((int) fxImage.getWidth(), (int) fxImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pngImage.getHeight(); y++)
            for (int x = 0; x < pngImage.getWidth(); x++) pngImage.setRGB(x, y, fxImage.getPixelReader().getArgb(x, y));
        Files.createDirectories(Path.of(filePath).getParent());
        ImageIO.write(pngImage, "png", Path.of(filePath).toFile());
    }

    // такое же хранилище в памяти, как в ParkingServiceTest, только у сотрудников полные имена и почты
    private static final class MemoryRepository implements ParkingRepository {
        private final List<Employee> employees = new ArrayList<>(List.of(
            new Employee(1, "Иван Петров", "petrov@company.ru"), new Employee(2, "Анна Смирнова", "smirnova@company.ru")));
        private final List<Booking> bookings = new ArrayList<>();
        private long nextBookingId = 1;
        public List<Employee> employees() { return List.copyOf(employees); }
        public Employee addEmployee(String name, String email) {
            var newEmployee = new Employee(employees.size() + 1, name, email);
            employees.add(newEmployee);
            return newEmployee;
        }
        public List<Booking> bookings() { return List.copyOf(bookings); }
        public Booking save(Booking booking) {
            bookings.removeIf(storedBooking -> storedBooking.id() == booking.id());
            var savedBooking = new Booking(booking.id() == 0 ? nextBookingId++ : booking.id(),
                booking.employeeId(), booking.spot(), booking.date(), booking.status());
            bookings.add(savedBooking);
            return savedBooking;
        }
        public boolean delete(long id) { return bookings.removeIf(booking -> booking.id() == id); }
        public List<String> tables() { return List.of("employees", "bookings"); }
    }
}
