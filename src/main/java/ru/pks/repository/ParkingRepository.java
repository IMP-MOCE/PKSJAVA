package ru.pks.repository;

import ru.pks.model.Booking;
import ru.pks.model.Employee;
import java.util.List;

public interface ParkingRepository {
    List<Employee> employees();
    Employee addEmployee(String name, String email);
    List<Booking> bookings();
    Booking save(Booking booking);
    boolean delete(long id);
    List<String> tables();
}
