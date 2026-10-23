package ru.pks.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.pks.config.Database;
import ru.pks.repository.JdbcParkingRepository;
import ru.pks.service.ParkingService;

// запуск графической версии, javafx создаёт этот класс сам и вызывает start
public final class ParkingApplication extends Application {
    @Override
    public void start(Stage stage) {
        try {
            // та же цепочка, что и в консольной версии: бд -> репозиторий -> сервис, только сверху теперь окно
            var service = new ParkingService(new JdbcParkingRepository(Database.fromEnvironment()));
            var controller = new MainController(service);
            // грузим разметку окна из main.fxml и привязываем к ней наш контроллер
            var loader = new FXMLLoader(getClass().getResource("main.fxml"));
            loader.setController(controller);
            var scene = new Scene(loader.load(), 1080, 700);
            scene.getStylesheets().add(getClass().getResource("parking.css").toExternalForm());
            stage.setTitle("Корпоративная парковка");
            stage.setMinWidth(850);
            stage.setMinHeight(580);
            stage.setScene(scene);
            stage.show();
            // окно уже показали, теперь подтягиваем данные из базы
            controller.refresh();
        } catch (Exception e) {
            // не получилось запуститься (нет пароля, не загрузился fxml и т.п.), показываем ошибку и закрываемся
            FxDialogs.error(null, e);
            javafx.application.Platform.exit();
        }
    }
}
