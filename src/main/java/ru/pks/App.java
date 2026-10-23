package ru.pks;

import ru.pks.config.Database;
import ru.pks.repository.JdbcParkingRepository;
import ru.pks.service.ParkingService;
import ru.pks.ui.ConsoleMenu;
import ru.pks.ui.ParkingApplication;
import javafx.application.Application;
import java.util.Scanner;

// точка входа, отсюда стартует вся прога
// по дефолту открывается окно на javafx, а если запустить с флагом --console, то будет старое текстовое меню из кр1
public final class App {
    public static void main(String[] args) {
        boolean consoleModeRequested = args.length > 0 && args[0].equals("--console");
        if (!consoleModeRequested) {
            // javafx сам создаст ParkingApplication и вызовет у него start(), дальше всё там
            Application.launch(ParkingApplication.class, args);
            return;
        }
        try {
            // собираем цепочку руками: настройки бд -> репозиторий (sql) -> сервис (правила) -> меню
            var service = new ParkingService(new JdbcParkingRepository(Database.fromEnvironment()));
            new ConsoleMenu(service, new Scanner(System.in)).run();
        } catch (IllegalStateException e) {
            // сюда прилетает, если не задан пароль от бд, просто пишем причину и выходим с кодом ошибки
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
