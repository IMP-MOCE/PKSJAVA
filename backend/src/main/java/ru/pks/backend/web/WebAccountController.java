package ru.pks.backend.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import ru.pks.backend.dto.AuthDtos.ChangePasswordRequest;
import ru.pks.backend.dto.AuthDtos.InitialPasswordChangeRequest;
import ru.pks.backend.security.UserPrincipal;
import ru.pks.backend.service.AuthService;

@Controller
public class WebAccountController {
    private final AuthService auth;

    public WebAccountController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/")
    public String home() { return "redirect:/bookings"; }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/password")
    public String password() { return "password"; }

    @PostMapping("/password/initial")
    public String changeInitial(@Valid @ModelAttribute InitialPasswordChangeRequest form,
            @AuthenticationPrincipal UserPrincipal user, HttpServletRequest request) {
        if (!user.passwordChangeRequired()) return "redirect:/password";
        auth.changeInitialPassword(form);
        return signOut(request);
    }

    @PostMapping("/password")
    public String change(@Valid @ModelAttribute ChangePasswordRequest form,
            @AuthenticationPrincipal UserPrincipal user, HttpServletRequest request) {
        if (user.passwordChangeRequired()) return "redirect:/password";
        auth.changePassword(form);
        return signOut(request);
    }

    private String signOut(HttpServletRequest request) {
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return "redirect:/login?changed";
    }
}
