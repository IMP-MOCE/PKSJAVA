package ru.pks.repository;

import ru.pks.model.Booking;
import ru.pks.model.Employee;
import java.util.List;

// это интерфейс бдшки, сервис работает только с ним и не знает, что там внутри
// поэтому реализацию можно спокойно подменить: в проге это JdbcParkingRepository (postgres),
// а в тестах MemoryRepository, который хранит всё в обычных списках
// никаких проверок тут нет, просто сохранить/достать, все правила живут в ParkingService
public interface ParkingRepository {
    // все сотрудники по порядку id
    List<Employee> employees();

    // добавить сотрудника, вернёт его уже с id от базы
    Employee addEmployee(String name, String email);

    // все брони по порядку id
    List<Booking> bookings();

    // если у брони id = 0, то вставляем новую, иначе обновляем существующую
    // возвращает сохранённую бронь с настоящим id
    Booking save(Booking booking);

    // удалить бронь, вернёт false если такой и не было
    boolean delete(long id);

    // названия таблиц в бд, нужно только для пункта меню "таблицы бд"
    List<String> tables();
}
