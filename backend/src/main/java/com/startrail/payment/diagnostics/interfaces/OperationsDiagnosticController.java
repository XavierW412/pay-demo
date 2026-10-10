package com.startrail.payment.diagnostics.interfaces;

import com.startrail.payment.diagnostics.application.DiagnosticIncidentRepository;
import com.startrail.payment.diagnostics.application.DiagnosticIncidentService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/diagnostics")
public class OperationsDiagnosticController {
    private static final Set<String> FREE_TEXT_ROLES = Set.of(
            "ROLE_STAFF_ADMIN", "ROLE_STAFF_OPERATOR", "ROLE_TEMP_TESTER");
    private final DiagnosticIncidentService service;
    private final DiagnosticRequestReader reader;
    public OperationsDiagnosticController(DiagnosticIncidentService service, DiagnosticRequestReader reader) {
        this.service = service;
        this.reader = reader;
    }
    @GetMapping
    public DiagnosticIncidentResponse.Page list(Authentication authentication,
            @RequestParam(required = false) UUID diagnosticId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String module, @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return DiagnosticIncidentResponse.Page.from(service.list(authentication,
                new DiagnosticIncidentRepository.Query(diagnosticId, from, to, module, severity, status, page, size)));
    }
    @GetMapping("/{diagnosticId}")
    public DiagnosticIncidentResponse.Detail detail(Authentication authentication, @PathVariable UUID diagnosticId) {
        return DiagnosticIncidentResponse.Detail.from(service.detail(authentication, diagnosticId), canViewFreeText(authentication));
    }
    @PostMapping("/{diagnosticId}/acknowledge")
    public DiagnosticIncidentResponse.Detail acknowledge(Authentication authentication, @PathVariable UUID diagnosticId,
            HttpServletRequest request) throws IOException {
        return handle(authentication, diagnosticId, "ACKNOWLEDGE", request);
    }
    @PostMapping("/{diagnosticId}/notes")
    public DiagnosticIncidentResponse.Detail note(Authentication authentication, @PathVariable UUID diagnosticId,
            HttpServletRequest request) throws IOException {
        return handle(authentication, diagnosticId, "NOTE", request);
    }
    @PostMapping("/{diagnosticId}/resolve")
    public DiagnosticIncidentResponse.Detail resolve(Authentication authentication, @PathVariable UUID diagnosticId,
            HttpServletRequest request) throws IOException {
        return handle(authentication, diagnosticId, "RESOLVE", request);
    }
    private DiagnosticIncidentResponse.Detail handle(Authentication actor, UUID id, String action,
            HttpServletRequest request) throws IOException {
        NoteRequest body = reader.read(request, Set.of("note"), NoteRequest.class, 4096);
        return DiagnosticIncidentResponse.Detail.from(service.handle(actor, id, action, body.note()), true);
    }
    private boolean canViewFreeText(Authentication actor) {
        return actor.getAuthorities().stream().anyMatch(authority -> FREE_TEXT_ROLES.contains(authority.getAuthority()));
    }
    public record NoteRequest(String note) {}
}
