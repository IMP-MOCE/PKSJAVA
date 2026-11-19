package ru.pks.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.pks.backend.domain.Employee;
import ru.pks.backend.dto.EmployeeDtos;
import ru.pks.backend.error.ConflictException;
import ru.pks.backend.error.NotFoundException;
import ru.pks.backend.repository.EmployeeRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EmployeeService {
    private final EmployeeRepository employees;
    private final AuditService audit;

    public EmployeeService(EmployeeRepository employees, AuditService audit) {
        this.employees = employees;
        this.audit = audit;
    }

    public List<EmployeeDtos.Response> list() {
        return employees.findAllByOrderByNameAsc().stream().map(this::response).toList();
    }

    @Transactional
    public EmployeeDtos.Response save(UUID id, EmployeeDtos.SaveRequest request) {
        Employee employee = id == null ? new Employee() : employees.findByUuid(id)
                .orElseThrow(() -> new NotFoundException("Сотрудник не найден"));
        if (id != null && employee.getVersion() != request.version())
            throw new ConflictException("Сотрудник уже изменен, обновите список");
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        employees.findByEmailIgnoreCase(email).ifPresent(other -> {
            if (!other.getUuid().equals(employee.getUuid())) throw new ConflictException("Такая почта уже есть");
        });
        employee.setName(request.name().strip());
        employee.setEmail(email);
        employee.setActive(request.active());
        employees.saveAndFlush(employee);
        audit.record(id == null ? "EMPLOYEE_CREATED" : "EMPLOYEE_UPDATED", "EMPLOYEE", employee.getUuid(),
                "Сохранен сотрудник " + employee.getName(), Map.of());
        return response(employee);
    }

    private EmployeeDtos.Response response(Employee employee) {
        return new EmployeeDtos.Response(employee.getUuid(), employee.getId(), employee.getName(),
                employee.getEmail(), employee.isActive(), employee.getVersion());
    }
}
