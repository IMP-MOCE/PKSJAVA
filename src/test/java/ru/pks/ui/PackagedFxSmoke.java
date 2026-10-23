package ru.pks.ui;

import javafx.application.Platform;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

// это не junit тест, а обычная программа с main
// PostgresIntegrationTest запускает её отдельным процессом поверх готового jar, а не поверх target/classes,
// чтобы убедиться, что собранный jar реально открывает окно и видит 10 броней из seed.sql
// если что-то не так, кидаем ошибку, процесс завершается с ненулевым кодом, и тест это ловит
public final class PackagedFxSmoke {
    public static void main(String[] args) throws Exception {
        // защёлка: главный поток ждёт, пока поток javafx не скажет "я всё"
        var windowChecked = new CountDownLatch(1);
        // ошибку из потока javafx так просто не пробросить, поэтому складываем её сюда и проверяем потом
        var failure = new AtomicReference<Throwable>();
        Platform.startup(() -> {
            var stage = new Stage();
            try {
                new ParkingApplication().start(stage);
                var bookingTable = (TableView<?>) stage.getScene().lookup("#bookingTable");
                if (bookingTable.getItems().size() != 10) throw new AssertionError("Ожидалось 10 учебных бронирований");
            } catch (Throwable e) { failure.set(e); }
            finally { stage.close(); windowChecked.countDown(); }
        });
        boolean finishedInTime = windowChecked.await(30, TimeUnit.SECONDS);
        Platform.exit();
        if (!finishedInTime) throw new AssertionError("Графическое приложение не запустилось");
        if (failure.get() != null) throw new AssertionError("Ошибка готового JavaFX JAR", failure.get());
    }
}
