package ru.pks.backend.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RequestMetadata {
    private final HttpServletRequest request;

    public RequestMetadata(HttpServletRequest request) {
        this.request = request;
    }

    public String correlationId() { return MDC.get("correlationId"); }
    public String userAgent() { return trim(request.getHeader("User-Agent"), 500); }

    public String ipAddress() { return trim(request.getRemoteAddr(), 64); }

    private static String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
