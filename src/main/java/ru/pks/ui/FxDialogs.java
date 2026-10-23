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

// сборник мелких помощников для окна: окошко с ошибкой, диалог-форма, работа с датами и статусами
// всё static, объект класса создавать не нужно
final class FxDialogs {
    private FxDialogs() { }

    // пишет ошибку в лог и показывает пользователю окошко с её текстом
    static void error(Window owner, Exception e) {
        System.getLogger("parking").log(System.Logger.Level.ERROR, "Ошибка операции", e);
        String errorText = e.getMessage() == null ? "Не удалось выполнить операцию." : e.getMessage();
        var alert = new Alert(Alert.AlertType.ERROR, errorText);
        alert.initOwner(owner);
        alert.setTitle("Ошибка");
        alert.setHeaderText("Проверьте данные или подключение к базе");
        alert.showAndWait();
    }

    // открывает модальный диалог с формой из fxml файла и кнопками "Сохранить" / "Отмена"
    // по "Сохранить" вызывается saveAction, если он кинул ошибку, текст показывается под полями, а окно не закрывается
    // вернёт true, если в итоге сохранили, и false, если нажали отмену или закрыли окно
    static boolean form(Window owner, String title, String fxmlFile, Object controller, Runnable saveAction) {
        try {
            var loader = new FXMLLoader(FxDialogs.class.getResource(fxmlFile));
            loader.setController(controller);
            // складываем друг под другом: сама форма из fxml, а под ней строчка для ошибки
            var content = new VBox(12);
            content.getChildren().add(loader.load());
            var errorLabel = new Label();
            errorLabel.setWrapText(true);
            errorLabel.getStyleClass().add("error");
            content.getChildren().add(errorLabel);
            var dialog = new Dialog<ButtonType>();
            dialog.initOwner(owner);
            dialog.setTitle(title);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getStylesheets().add(FxDialogs.class.getResource("parking.css").toExternalForm());
            var saveButtonType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
            // фильтр срабатывает раньше, чем диалог успеет закрыться по нажатию кнопки
            dialog.getDialogPane().lookupButton(saveButtonType).addEventFilter(ActionEvent.ACTION, clickEvent -> {
                try { saveAction.run(); }
                catch (RuntimeException e) {
                    System.getLogger("parking").log(System.Logger.Level.WARNING, "Не удалось сохранить форму", e);
                    errorLabel.setText(e.getMessage() == null ? "Не удалось сохранить данные." : e.getMessage());
                    // consume "съедает" нажатие, и диалог не закрывается, так введённые данные не пропадают
                    clickEvent.consume();
                }
            });
            // showAndWait ждёт, пока окно закроют, и возвращает нажатую кнопку
            return dialog.showAndWait().orElse(ButtonType.CANCEL) == saveButtonType;
        } catch (IOException e) { throw new IllegalStateException("Не удалось открыть форму.", e); }
    }

    // настраиваем поле даты на формат ГГГГ-ММ-ДД (например 2026-10-23) в обе стороны: дата -> текст и текст -> дата
    static void dateFormat(DatePicker datePicker) {
        datePicker.setPromptText("ГГГГ-ММ-ДД");
        datePicker.setConverter(new StringConverter<>() {
            public String toString(LocalDate date) { return date == null ? "" : date.toString(); }
            public LocalDate fromString(String text) { return text == null || text.isBlank() ? null : LocalDate.parse(text.strip()); }
        });
    }

    // достаём дату из поля
    // читаем именно набранный текст, а не getValue(), потому что если человек вписал дату руками
    // и не нажал enter, то в value ещё лежит старое значение
    // isRequired = true значит, что пустое поле считается ошибкой, иначе пустое поле вернёт null
    static LocalDate date(DatePicker datePicker, boolean isRequired) {
        LocalDate parsedDate;
        try { parsedDate = datePicker.getConverter().fromString(datePicker.getEditor().getText()); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Введите существующую дату в формате ГГГГ-ММ-ДД."); }
        if (isRequired && parsedDate == null) throw new IllegalArgumentException("Укажите дату бронирования.");
        datePicker.setValue(parsedDate);
        return parsedDate;
    }

    // перевод статуса на русский для интерфейса, null тут значит "фильтр не выбран"
    static String status(BookingStatus status) {
        if (status == null) return "Все статусы";
        return switch (status) {
            case CREATED -> "Создано";
            case CONFIRMED -> "Подтверждено";
            case COMPLETED -> "Завершено";
            case CANCELLED -> "Отменено";
        };
    }

    // заполняет выпадающий список статусами и показывает их по-русски
    // includeAllOption = true добавляет первым пунктом null, то есть "Все статусы", это нужно для фильтра
    static void statuses(ComboBox<BookingStatus> statusComboBox, boolean includeAllOption) {
        if (includeAllOption) statusComboBox.getItems().add(null);
        statusComboBox.getItems().addAll(BookingStatus.values());
        statusComboBox.setConverter(new StringConverter<>() {
            public String toString(BookingStatus status) { return status(status); }
            public BookingStatus fromString(String text) { throw new UnsupportedOperationException(); }
        });
    }
}
