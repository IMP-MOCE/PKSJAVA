package ru.pks.backend.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.pks.backend.config.AppProperties;
import ru.pks.backend.dto.AuthDtos.AuthResponse;
import ru.pks.backend.dto.AuthDtos.ChangePasswordRequest;
import ru.pks.backend.dto.AuthDtos.InitialPasswordChangeRequest;
import ru.pks.backend.dto.AuthDtos.ClientType;
import ru.pks.backend.dto.AuthDtos.CurrentUser;
import ru.pks.backend.dto.AuthDtos.LoginRequest;
import ru.pks.backend.dto.AuthDtos.RefreshRequest;
import ru.pks.backend.domain.Role;
import ru.pks.backend.service.AuthService;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "parking_refresh";
    private static final String SWAGGER_COOKIE = "parking_swagger";
    private final AuthService auth;
    private final AppProperties properties;

    public AuthController(AuthService auth, AppProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse issued = auth.login(request);
        if (request.effectiveClientType() == ClientType.WEB) {
            setRefreshCookie(response, issued.refreshToken(), issued.refreshExpiresAt());
            syncSwaggerCookie(response, issued);
            return withoutRefreshToken(issued);
        }
        return issued;
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody(required = false) RefreshRequest request,
                                @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
                                HttpServletResponse response) {
        boolean web = cookie != null && !cookie.isBlank();
        String raw = web ? cookie : request == null ? null : request.refreshToken();
        AuthResponse issued = auth.refresh(raw);
        if (web) {
            setRefreshCookie(response, issued.refreshToken(), issued.refreshExpiresAt());
            syncSwaggerCookie(response, issued);
            return withoutRefreshToken(issued);
        }
        return issued;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request,
                                       @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
                                       HttpServletResponse response) {
        auth.logout(cookie != null ? cookie : request == null ? null : request.refreshToken());
        clearRefreshCookie(response);
        clearSwaggerCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUser me() {
        return auth.me();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-initial-password")
    public ResponseEntity<Void> changeInitialPassword(
            @Valid @RequestBody InitialPasswordChangeRequest request) {
        auth.changeInitialPassword(request);
        return ResponseEntity.noContent().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String value, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(properties.cookieSecure()).sameSite("Strict")
                .path("/api/auth").maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void syncSwaggerCookie(HttpServletResponse response, AuthResponse issued) {
        boolean admin = issued.user().roles().contains(Role.ADMIN);
        boolean permanentPassword = !issued.user().passwordChangeRequired();

        if (!admin || !permanentPassword) {
            clearSwaggerCookie(response);
            return;
        }

        Duration maxAge = Duration.between(Instant.now(), issued.accessExpiresAt());

        ResponseCookie cookie = ResponseCookie.from(SWAGGER_COOKIE, issued.accessToken())
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearSwaggerCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(SWAGGER_COOKIE, "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static AuthResponse withoutRefreshToken(AuthResponse source) {
        return new AuthResponse(source.accessToken(), source.accessExpiresAt(), null,
                source.refreshExpiresAt(), source.user());
    }
}
