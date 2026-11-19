package ru.pks.backend.web;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.pks.backend.dto.EmployeeDtos;
import ru.pks.backend.dto.ImportDtos;
import ru.pks.backend.error.NotFoundException;
import ru.pks.backend.service.*;
import java.util.UUID;

@Controller
public class WebParkingController {
    private final EmployeeService employees;
    private final AnalyticsService analytics;
    private final CsvBookingService csv;

    public WebParkingController(EmployeeService employees, AnalyticsService analytics, CsvBookingService csv) {
        this.employees = employees;
        this.analytics = analytics;
        this.csv = csv;
    }

    @GetMapping("/employees")
    public String employees(Model model) {
        model.addAttribute("employees", employees.list());
        return "employees";
    }

    @GetMapping("/employees/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newEmployee() { return "employee-form"; }

    @GetMapping("/employees/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String employeeForm(@PathVariable UUID id, Model model) {
        var employee = employees.list().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("Сотрудник не найден"));
        model.addAttribute("employee", employee);
        return "employee-form";
    }

    @PostMapping({"/employees", "/employees/{id}/edit"})
    @PreAuthorize("hasRole('ADMIN')")
    public String saveEmployee(@PathVariable(required = false) UUID id,
            @Valid @ModelAttribute EmployeeDtos.SaveRequest form, RedirectAttributes redirect) {
        employees.save(id, form);
        redirect.addFlashAttribute("message", "Сотрудник сохранён");
        return "redirect:/employees";
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        model.addAttribute("summary", analytics.summary());
        return "analytics";
    }

    @GetMapping("/import")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String csvForm() { return "import"; }

    @PostMapping("/import/preview")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String preview(@RequestParam MultipartFile file, @RequestParam ImportDtos.Mode mode,
            RedirectAttributes redirect) {
        redirect.addFlashAttribute("preview", csv.preview(file, mode));
        return "redirect:/import";
    }

    @PostMapping("/import/apply")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public String apply(@Valid @ModelAttribute ImportDtos.ApplyRequest form, RedirectAttributes redirect) {
        redirect.addFlashAttribute("applied", csv.apply(form.previewId()));
        return "redirect:/import";
    }
}
