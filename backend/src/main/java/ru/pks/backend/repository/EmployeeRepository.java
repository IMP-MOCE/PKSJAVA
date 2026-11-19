package ru.pks.backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.pks.backend.domain.Employee;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUuid(UUID uuid);
    Optional<Employee> findByEmailIgnoreCase(String email);
    List<Employee> findAllByOrderByNameAsc();

    // блокировка сериализует бронирования одного сотрудника в разных запросах
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.uuid = :uuid")
    Optional<Employee> lockByUuid(UUID uuid);
}
