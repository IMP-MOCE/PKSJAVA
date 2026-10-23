package ru.pks.ui;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import ru.pks.service.ParkingService;

// контроллер формы нового сотрудника (разметка в employee-form.fxml)
// тут два поля и сохранение, все проверки имени и почты делает сервис
final class EmployeeFormController {
    private final ParkingService service;
    @FXML private TextField nameField;
    @FXML private TextField emailField;

    EmployeeFormController(ParkingService service) { this.service = service; }

    // вызывается по кнопке "Сохранить", если данные кривые, сервис кинет ошибку и форма её покажет
    void save() { service.addEmployee(nameField.getText(), emailField.getText()); }
}
