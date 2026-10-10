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

@EnabledIfEnvironmentVariable(named = "RUN_FX_TESTS", matches = "true")
class JavaFxTest {
    @BeforeAll static void startJavaFx() throws Exception {
        var started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }

    @AfterAll static void stopJavaFx() { Platform.exit(); }

    @Test void fxmlFormsTableFiltersAndDeleteConfirmation() throws Exception {
        var task = new FutureTask<Void>(() -> {
            var repo = new MemoryRepository();
            var service = new ParkingService(repo);
            var first = service.create(1, 24, LocalDate.now());
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
                controller.refresh();
                TableView<Booking> table = node(root, "bookingTable");
                assertEquals(2, table.getItems().size());
                TextField search = node(root, "search");
                search.setText("24");
                controller.refresh();
                assertEquals(List.of(first), List.copyOf(table.getItems()));
                search.clear();
                controller.refresh();
                TableColumn<Booking, Integer> spotColumn = (TableColumn<Booking, Integer>) table.getColumns().get(2);
                table.getSortOrder().setAll(spotColumn);
                assertEquals(12, table.getItems().getFirst().spot());
                table.getSelectionModel().select(first);
                assertTrue(((Label) root.lookup("#selectedEmployee")).getText().contains("petrov@company.ru"));
                snapshot(scene, "target/javafx-main.png");
                scene.setRoot(new javafx.scene.layout.VBox());
                var smallScene = new Scene(root, 850, 580);
                smallScene.getStylesheets().addAll(scene.getStylesheets());
                stage.setScene(smallScene);
                stage.sizeToScene();
                snapshot(smallScene, "target/javafx-small.png");

                // Нажимаем настоящую кнопку формы: неверный ввод не закрывает диалог.
                Platform.runLater(() -> {
                    Parent dialog = dialogRoot();
                    TextField spot = node(dialog, "spot");
                    ComboBox<Employee> person = node(dialog, "employee");
                    person.getSelectionModel().selectFirst();
                    DatePicker date = node(dialog, "date");
                    date.setValue(LocalDate.now().plusDays(2));
                    spot.setText("abc");
                    saveButton(dialog).fire();
                    assertEquals("abc", spot.getText());
                    assertTrue(((Label) dialog.lookup(".error")).getText().contains("целое"));
                    spot.setText("30");
                    saveButton(dialog).fire();
                });
                ((Button) root.lookup("#addBookingButton")).fire();
                assertEquals(3, table.getItems().size());

                table.getSelectionModel().select(first);
                Platform.runLater(() -> {
                    Parent dialog = dialogRoot();
                    TextField spot = node(dialog, "spot");
                    assertEquals("24", spot.getText());
                    assertTrue(((ComboBox<?>) dialog.lookup("#employee")).isDisabled());
                    spot.setText("26");
                    saveButton(dialog).fire();
                });
                ((Button) root.lookup("#editButton")).fire();
                Booking edited = service.get(first.id());
                assertEquals(26, edited.spot());
                table.getSelectionModel().select(edited);
                ComboBox<BookingStatus> status = node(root, "newStatus");
                status.setValue(BookingStatus.CONFIRMED);
                ((Button) root.lookup("#statusButton")).fire();
                assertEquals(BookingStatus.CONFIRMED, service.get(first.id()).status());

                table.getSelectionModel().select(service.get(first.id()));
                Platform.runLater(() -> ((Button) ((DialogPane) dialogRoot()).lookupButton(ButtonType.NO)).fire());
                ((Button) root.lookup("#deleteButton")).fire();
                assertEquals(3, table.getItems().size());
                table.getSelectionModel().select(service.get(first.id()));
                Platform.runLater(() -> ((Button) ((DialogPane) dialogRoot()).lookupButton(ButtonType.YES)).fire());
                ((Button) root.lookup("#deleteButton")).fire();
                assertEquals(2, table.getItems().size());

                var form = new EmployeeFormController(service);
                var employeeLoader = new FXMLLoader(MainController.class.getResource("employee-form.fxml"));
                employeeLoader.setController(form);
                Parent employeeRoot = employeeLoader.load();
                ((TextField) employeeRoot.lookup("#name")).setText("Мария");
                ((TextField) employeeRoot.lookup("#email")).setText("maria@company.ru");
                form.save();
                assertEquals(3, service.employees().size());
            } finally { stage.close(); }
            return null;
        });
        Platform.runLater(task);
        task.get(45, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static <T> T node(Parent root, String id) { return (T) root.lookup("#" + id); }
    private static Parent dialogRoot() {
        return Window.getWindows().stream().filter(Window::isShowing).reduce((a, b) -> b).orElseThrow().getScene().getRoot();
    }
    private static Button saveButton(Parent root) {
        var pane = (DialogPane) root;
        return (Button) pane.lookupButton(pane.getButtonTypes().stream()
            .filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow());
    }
    private static void snapshot(Scene scene, String file) throws Exception {
        var image = scene.snapshot(null);
        var png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++)
            for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        Files.createDirectories(Path.of(file).getParent());
        ImageIO.write(png, "png", Path.of(file).toFile());
    }

    private static final class MemoryRepository implements ParkingRepository {
        private final List<Employee> employees = new ArrayList<>(List.of(
            new Employee(1, "Иван Петров", "petrov@company.ru"), new Employee(2, "Анна Смирнова", "smirnova@company.ru")));
        private final List<Booking> bookings = new ArrayList<>();
        private long nextId = 1;
        public List<Employee> employees() { return List.copyOf(employees); }
        public Employee addEmployee(String name, String email) {
            var employee = new Employee(employees.size() + 1, name, email);
            employees.add(employee);
            return employee;
        }
        public List<Booking> bookings() { return List.copyOf(bookings); }
        public Booking save(Booking b) {
            bookings.removeIf(old -> old.id() == b.id());
            var saved = new Booking(b.id() == 0 ? nextId++ : b.id(), b.employeeId(), b.spot(), b.date(), b.status());
            bookings.add(saved);
            return saved;
        }
        public boolean delete(long id) { return bookings.removeIf(b -> b.id() == id); }
        public List<String> tables() { return List.of("employees", "bookings"); }
    }
}
