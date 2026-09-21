package ru.pks;

import ru.pks.config.Database;
import ru.pks.repository.JdbcParkingRepository;
import ru.pks.service.ParkingService;
import ru.pks.ui.ConsoleMenu;
import java.util.Scanner;

public final class App {
    public static void main(String[] args) {
        try {
            var service = new ParkingService(new JdbcParkingRepository(Database.fromEnvironment()));
            new ConsoleMenu(service, new Scanner(System.in)).run();
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
