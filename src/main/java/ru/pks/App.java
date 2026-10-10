package ru.pks;

import ru.pks.config.Database;
import ru.pks.repository.JdbcParkingRepository;
import ru.pks.service.ParkingService;
import ru.pks.ui.ConsoleMenu;
import ru.pks.ui.ParkingApplication;
import javafx.application.Application;
import java.util.Scanner;

public final class App {
    public static void main(String[] args) {
        if (args.length == 0 || !args[0].equals("--console")) {
            Application.launch(ParkingApplication.class, args);
            return;
        }
        try {
            var service = new ParkingService(new JdbcParkingRepository(Database.fromEnvironment()));
            new ConsoleMenu(service, new Scanner(System.in)).run();
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
