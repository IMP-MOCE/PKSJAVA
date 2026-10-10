package ru.pks.ui;

import javafx.application.Platform;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Запускается отдельной JVM с готовым JAR, а не с target/classes. */
public final class PackagedFxSmoke {
    public static void main(String[] args) throws Exception {
        var done = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Platform.startup(() -> {
            var stage = new Stage();
            try {
                new ParkingApplication().start(stage);
                var table = (TableView<?>) stage.getScene().lookup("#bookingTable");
                if (table.getItems().size() != 10) throw new AssertionError("Ожидалось 10 учебных бронирований");
            } catch (Throwable e) { failure.set(e); }
            finally { stage.close(); done.countDown(); }
        });
        boolean completed = done.await(30, TimeUnit.SECONDS);
        Platform.exit();
        if (!completed) throw new AssertionError("Графическое приложение не запустилось");
        if (failure.get() != null) throw new AssertionError("Ошибка готового JavaFX JAR", failure.get());
    }
}
