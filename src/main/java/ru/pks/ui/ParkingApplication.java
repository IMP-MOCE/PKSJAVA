package ru.pks.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.pks.config.Database;
import ru.pks.repository.JdbcParkingRepository;
import ru.pks.service.ParkingService;

public final class ParkingApplication extends Application {
    @Override
    public void start(Stage stage) {
        try {
            var service = new ParkingService(new JdbcParkingRepository(Database.fromEnvironment()));
            var controller = new MainController(service);
            var loader = new FXMLLoader(getClass().getResource("main.fxml"));
            loader.setController(controller);
            var scene = new Scene(loader.load(), 1080, 700);
            scene.getStylesheets().add(getClass().getResource("parking.css").toExternalForm());
            stage.setTitle("Корпоративная парковка");
            stage.setMinWidth(850);
            stage.setMinHeight(580);
            stage.setScene(scene);
            stage.show();
            controller.refresh();
        } catch (Exception e) {
            FxDialogs.error(null, e);
            javafx.application.Platform.exit();
        }
    }
}
