package ru.pks.backend.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.pks.backend.dto.EmployeeDtos;
import ru.pks.backend.service.EmployeeService;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class EmployeeController {
    private final EmployeeService employees;

    public EmployeeController(EmployeeService employees) { this.employees = employees; }

    @GetMapping
    public List<EmployeeDtos.Response> list() { return employees.list(); }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeDtos.Response create(@Valid @RequestBody EmployeeDtos.SaveRequest request) {
        return employees.save(null, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeDtos.Response update(@PathVariable UUID id, @Valid @RequestBody EmployeeDtos.SaveRequest request) {
        return employees.save(id, request);
    }
}
