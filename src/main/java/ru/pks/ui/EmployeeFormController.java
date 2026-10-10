package ru.pks.ui;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import ru.pks.service.ParkingService;

final class EmployeeFormController {
    private final ParkingService service;
    @FXML private TextField name;
    @FXML private TextField email;

    EmployeeFormController(ParkingService service) { this.service = service; }

    void save() { service.addEmployee(name.getText(), email.getText()); }
}
