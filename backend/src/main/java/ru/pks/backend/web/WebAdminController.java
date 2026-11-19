package ru.pks.backend.web;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.pks.backend.dto.UserDtos;
import ru.pks.backend.service.AuditService;
import ru.pks.backend.service.UserAdminService;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class WebAdminController {
    private final UserAdminService users;
    private final AuditService audit;
    private final PageableFactory pages;

    public WebAdminController(UserAdminService users, AuditService audit, PageableFactory pages) {
        this.users = users;
        this.audit = audit;
        this.pages = pages;
    }

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("result", users.list(pages.users(page, 25, "email,asc")));
        return "users";
    }

    @GetMapping("/users/new")
    public String newUser() { return "user-form"; }

    @GetMapping("/users/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("editedUser", users.get(id));
        return "user-form";
    }

    @PostMapping("/users")
    public String create(@Valid @ModelAttribute UserDtos.CreateUserRequest form, RedirectAttributes redirect) {
        users.create(form);
        return saved(redirect);
    }

    @PostMapping("/users/{id}/edit")
    public String edit(@PathVariable UUID id, @Valid @ModelAttribute UserDtos.UpdateUserRequest form,
            RedirectAttributes redirect) {
        users.update(id, form);
        return saved(redirect);
    }

    @GetMapping("/users/{id}/reset")
    public String resetForm(@PathVariable UUID id, Model model) {
        model.addAttribute("editedUser", users.get(id));
        return "reset-password";
    }

    @PostMapping("/users/{id}/reset")
    public String reset(@PathVariable UUID id, @Valid @ModelAttribute UserDtos.ResetPasswordRequest form,
            RedirectAttributes redirect) {
        users.resetPassword(id, form);
        redirect.addFlashAttribute("message", "Временный пароль установлен");
        return "redirect:/admin/users";
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "occurred,desc") String sort, Model model) {
        model.addAttribute("result", audit.search(q, pages.audit(page, 25, sort)));
        model.addAttribute("q", q);
        model.addAttribute("sort", sort);
        return "audit";
    }

    private String saved(RedirectAttributes redirect) {
        redirect.addFlashAttribute("message", "Пользователь сохранён");
        return "redirect:/admin/users";
    }
}
