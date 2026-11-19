package ru.pks.backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.pks.backend.dto.AuditDtos.AuditResponse;
import ru.pks.backend.dto.PageResponse;
import ru.pks.backend.service.AuditService;
import ru.pks.backend.web.PageableFactory;

@RestController
@RequestMapping("/api/admin/audit")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditService audit;
    private final PageableFactory pageableFactory;

    public AuditController(AuditService audit, PageableFactory pageableFactory) {
        this.audit = audit;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    public PageResponse<AuditResponse> list(@RequestParam(defaultValue = "") String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "25") int size,
                                            @RequestParam(defaultValue = "occurred,desc") String sort) {
        return PageResponse.from(audit.search(q, pageableFactory.audit(page, size, sort)));
    }
}
