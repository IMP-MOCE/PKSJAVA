package ru.pks.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import ru.pks.model.BookingStatus;
import java.io.IOException;
import java.time.LocalDate;

final class FxDialogs {
    private FxDialogs() { }

    static void error(Window owner, Exception e) {
        System.getLogger("parking").log(System.Logger.Level.ERROR, "Ошибка операции", e);
        var alert = new Alert(Alert.AlertType.ERROR, e.getMessage() == null ? "Не удалось выполнить операцию." : e.getMessage());
        alert.initOwner(owner);
        alert.setTitle("Ошибка");
        alert.setHeaderText("Проверьте данные или подключение к базе");
        alert.showAndWait();
    }

    static boolean form(Window owner, String title, String file, Object controller, Runnable save) {
        try {
            var loader = new FXMLLoader(FxDialogs.class.getResource(file));
            loader.setController(controller);
            var content = new VBox(12);
            content.getChildren().add(loader.load());
            var error = new Label();
            error.setWrapText(true);
            error.getStyleClass().add("error");
            content.getChildren().add(error);
            var dialog = new Dialog<ButtonType>();
            dialog.initOwner(owner);
            dialog.setTitle(title);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getStylesheets().add(FxDialogs.class.getResource("parking.css").toExternalForm());
            var saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);
            dialog.getDialogPane().lookupButton(saveButton).addEventFilter(ActionEvent.ACTION, event -> {
                try { save.run(); }
                catch (RuntimeException e) {
                    System.getLogger("parking").log(System.Logger.Level.WARNING, "Не удалось сохранить форму", e);
                    error.setText(e.getMessage() == null ? "Не удалось сохранить данные." : e.getMessage());
                    event.consume(); // Ошибка не закрывает форму и не стирает ввод.
                }
            });
            return dialog.showAndWait().orElse(ButtonType.CANCEL) == saveButton;
        } catch (IOException e) { throw new IllegalStateException("Не удалось открыть форму.", e); }
    }

    static void dateFormat(DatePicker picker) {
        picker.setPromptText("ГГГГ-ММ-ДД");
        picker.setConverter(new StringConverter<>() {
            public String toString(LocalDate date) { return date == null ? "" : date.toString(); }
            public LocalDate fromString(String text) { return text == null || text.isBlank() ? null : LocalDate.parse(text.strip()); }
        });
    }

    static LocalDate date(DatePicker picker, boolean required) {
        LocalDate date;
        try { date = picker.getConverter().fromString(picker.getEditor().getText()); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Введите существующую дату в формате ГГГГ-ММ-ДД."); }
        if (required && date == null) throw new IllegalArgumentException("Укажите дату бронирования.");
        picker.setValue(date);
        return date;
    }

    static String status(BookingStatus status) {
        if (status == null) return "Все статусы";
        return switch (status) {
            case CREATED -> "Создано";
            case CONFIRMED -> "Подтверждено";
            case COMPLETED -> "Завершено";
            case CANCELLED -> "Отменено";
        };
    }

    static void statuses(ComboBox<BookingStatus> combo, boolean all) {
        if (all) combo.getItems().add(null);
        combo.getItems().addAll(BookingStatus.values());
        combo.setConverter(new StringConverter<>() {
            public String toString(BookingStatus value) { return status(value); }
            public BookingStatus fromString(String text) { throw new UnsupportedOperationException(); }
        });
    }
}
