package ru.pks.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.pks.backend.service.AnalyticsService;

@RestController
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping("/api/analytics/summary")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public AnalyticsService.Summary summary() { return analytics.summary(); }
}
