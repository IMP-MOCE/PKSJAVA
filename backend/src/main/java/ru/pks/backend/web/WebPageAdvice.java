package ru.pks.backend.web;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ru.pks.backend.domain.Role;
import ru.pks.backend.error.*;
import ru.pks.backend.security.UserPrincipal;

@ControllerAdvice(basePackageClasses = WebAccountController.class)
@Order(0)
public class WebPageAdvice {
    @ModelAttribute
    public void addUser(@AuthenticationPrincipal UserPrincipal user, Model model) {
        boolean admin = user != null && user.roles().contains(Role.ADMIN);
        model.addAttribute("user", user);
        model.addAttribute("admin", admin);
        model.addAttribute("canEdit", admin || user != null && user.roles().contains(Role.EDITOR));
        model.addAttribute("statusLabels", ParkingLabels.STATUS);
        model.addAttribute("urgencyLabels", ParkingLabels.URGENCY);
        model.addAttribute("roleLabels", ParkingLabels.ROLE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String invalidForm(MethodArgumentNotValidException ex, Model model, HttpServletResponse response) {
        model.addAttribute("fields", ex.getFieldErrors());
        return error(400, "Проверьте поля формы", model, response);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public String malformed(Model model, HttpServletResponse response) {
        return error(400, "Проверьте данные формы", model, response);
    }

    @ExceptionHandler(BadRequestException.class)
    public String badRequest(BadRequestException ex, Model model, HttpServletResponse response) {
        return error(400, ex.getMessage(), model, response);
    }

    @ExceptionHandler({ConflictException.class, OptimisticLockException.class,
            ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public String conflict(Exception ex, Model model, HttpServletResponse response) {
        String message = ex instanceof ConflictException ? ex.getMessage() : "Данные изменились или конфликтуют с другой записью";
        return error(409, message, model, response);
    }

    @ExceptionHandler(NotFoundException.class)
    public String missing(NotFoundException ex, Model model, HttpServletResponse response) {
        return error(404, ex.getMessage(), model, response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String denied(Model model, HttpServletResponse response) {
        return error(403, "Недостаточно прав", model, response);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public String incorrectPassword(Model model, HttpServletResponse response) {
        return error(401, "Неверный текущий пароль", model, response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(Model model, HttpServletResponse response) {
        return error(413, "Файл должен быть не больше 5 МБ", model, response);
    }

    private String error(int status, String message, Model model, HttpServletResponse response) {
        response.setStatus(status);
        model.addAttribute("problem", message);
        return "form-error";
    }
}
