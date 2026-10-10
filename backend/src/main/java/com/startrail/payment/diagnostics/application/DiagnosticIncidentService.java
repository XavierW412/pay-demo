package com.startrail.payment.diagnostics.application;

import com.startrail.payment.diagnostics.domain.DiagnosticIncident;
import com.startrail.payment.operations.application.OperatorAuditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DiagnosticIncidentService {
    private static final long DEDUPLICATION_WINDOW_MINUTES = 15;
    private static final Set<String> CATEGORIES = Set.of("UNHANDLED_ERROR", "PAGE_CRASH", "NETWORK_ERROR", "API_ERROR", "USER_REPORT");
    private static final Set<String> BROWSER_FAMILIES = Set.of("CHROMIUM", "FIREFOX", "SAFARI", "OTHER", "UNKNOWN");
    private static final Set<String> SEVERITIES = Set.of("ERROR", "WARNING", "INFO");
    private static final Set<String> MODULES = Set.of("CHECKOUT", "OPERATIONS", "SIMULATOR");
    private static final Set<String> ENVIRONMENTS = Set.of("local", "sandbox", "production", "development", "test");
    private static final Set<String> STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED");
    private static final Set<String> ROUTES = Set.of("/", "/unknown", "/checkout/:opaqueToken",
            "/checkout/:opaqueToken/result", "/checkout/return/:opaqueToken", "/operations",
            "/operations/login", "/operations/register/:token", "/operations/finance",
            "/operations/transactions", "/operations/transactions/:paymentOrderId",
            "/operations/audit", "/operations/reconciliation", "/operations/team",
            "/operations/diagnostics", "/operations/diagnostics/:diagnosticId", "/mock-xingzhe",
            "/mock-xingzhe/orders", "/mock-xingzhe/order/:businessOrderId");
    private final DiagnosticIncidentRepository repository;
    private final DiagnosticIdGenerator ids;
    private final OperatorAuditService auditService;
    private final String deploymentEnvironment;

    public DiagnosticIncidentService(DiagnosticIncidentRepository repository, DiagnosticIdGenerator ids,
            OperatorAuditService auditService,
            Environment environment, @Value("${diagnostics.environment:development}") String configuredEnvironment) {
        this.repository = repository;
        this.ids = ids;
        this.auditService = auditService;
        this.deploymentEnvironment = environment.acceptsProfiles(Profiles.of("production")) ? "production"
                : environment.acceptsProfiles(Profiles.of("local")) ? "local" : configuredEnvironment;
        if (!ENVIRONMENTS.contains(deploymentEnvironment)) throw new IllegalArgumentException("诊断环境配置无效");
    }

    @Transactional
    public Receipt receive(ClientEvent request) {
        Instant now = Instant.now();
        if (request.occurredAt() == null || request.occurredAt().isAfter(now.plusSeconds(300))
                || request.occurredAt().isBefore(now.minusSeconds(604800))) invalid("事件发生时间无效");
        if (request.environment() != null) allowed(request.environment(), ENVIRONMENTS);
        String browserFamily = request.browserFamily() == null ? "UNKNOWN" : request.browserFamily();
        allowed(browserFamily, BROWSER_FAMILIES);
        allowed(request.category(), CATEGORIES);
        allowed(request.severity(), SEVERITIES);
        allowed(request.module(), MODULES);
        allowed(request.routeTemplate(), ROUTES);
        if (deploymentEnvironment.equals("production") && (request.module().equals("SIMULATOR")
                || request.routeTemplate().startsWith("/mock-xingzhe"))) invalid("正式环境不接收模拟器事件");
        if (request.appVersion() == null || !request.appVersion().matches("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}(?:-[a-z]{1,16})?")) {
            invalid("应用版本格式无效");
        }
        uuid(request.requestId());
        uuid(request.relatedDiagnosticId());
        if (request.traceId() != null && !request.traceId().matches("[a-f0-9]{32}")) invalid("追踪编号格式无效");
        String summary = DiagnosticTextSanitizer.sanitize(request.summary(), 500);
        if (summary == null) summary = "客户端技术事件：" + categoryLabel(request.category());
        String description = DiagnosticTextSanitizer.sanitize(request.userDescription(), 1000);
        Instant receivedAt = Instant.now();
        String fingerprint = fingerprint(request.category() + "|" + request.severity() + "|" + request.module()
                + "|" + request.routeTemplate() + "|" + request.appVersion());
        boolean aggregate = !request.category().equals("USER_REPORT") && description == null;
        if (aggregate) {
            repository.lockFingerprint(fingerprint);
            receivedAt = Instant.now();
            var existing = repository.findRecentOpenByFingerprint(
                    fingerprint, receivedAt.minusSeconds(DEDUPLICATION_WINDOW_MINUTES * 60));
            if (existing.isPresent()) {
                var incident = existing.get();
                if (repository.recordOccurrence(incident.diagnosticId(), receivedAt)) {
                    return new Receipt(incident.diagnosticId(), receivedAt);
                }
            }
        }

        UUID id = UUID.fromString(ids.generate());
        repository.insert(new DiagnosticIncident(id, fingerprint, request.occurredAt(), receivedAt, receivedAt, 1,
                deploymentEnvironment, request.appVersion(), browserFamily, request.category(), request.severity(), request.module(),
                request.routeTemplate(), request.requestId(), request.traceId(), request.relatedDiagnosticId(),
                null, summary, description, "OPEN", null));
        return new Receipt(id, receivedAt);
    }

    @Transactional(readOnly = true)
    public Page list(Authentication actor, DiagnosticIncidentRepository.Query query) {
        authorize(actor, false);
        if (query.page() < 0 || query.page() > 100000 || query.size() < 1 || query.size() > 100) invalid("分页参数无效");
        if (query.module() != null) allowed(query.module(), MODULES);
        if (query.severity() != null) allowed(query.severity(), SEVERITIES);
        if (query.status() != null) allowed(query.status(), STATUSES);
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) invalid("查询时间范围无效");
        return new Page(repository.list(query), query.page(), query.size(), repository.count(query));
    }

    @Transactional(readOnly = true)
    public Detail detail(Authentication actor, UUID id) {
        authorize(actor, false);
        return new Detail(find(id, false), repository.history(id));
    }

    @Transactional
    public Detail handle(Authentication actor, UUID id, String action, String note) {
        authorize(actor, true);
        DiagnosticIncident incident = find(id, true);
        String nextStatus = switch (action) {
            case "ACKNOWLEDGE" -> "ACKNOWLEDGED";
            case "RESOLVE" -> "RESOLVED";
            case "NOTE" -> incident.status();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "诊断操作无效");
        };
        if (!action.equals("NOTE") && (incident.status().equals("RESOLVED") || incident.status().equals(nextStatus))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "事件已处于该状态或已经完成");
        }
        String safeNote = DiagnosticTextSanitizer.sanitize(note, 1000);
        if (action.equals("NOTE") && safeNote == null) invalid("请填写处理备注");
        repository.handle(id, nextStatus, actor.getName(), action, safeNote, Instant.now());
        String auditAction = switch (action) {
            case "ACKNOWLEDGE" -> "ACKNOWLEDGE_DIAGNOSTIC";
            case "NOTE" -> "ADD_DIAGNOSTIC_NOTE";
            case "RESOLVE" -> "RESOLVE_DIAGNOSTIC";
            default -> throw new IllegalStateException("诊断操作无效");
        };
        auditService.record(actor.getName(), null, auditAction, "SUCCEEDED",
                "诊断编号 " + id + "；操作 " + action);
        return new Detail(find(id, false), repository.history(id));
    }

    private DiagnosticIncident find(UUID id, boolean lock) {
        return repository.find(id, lock).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "诊断事件不存在"));
    }
    private void authorize(Authentication actor, boolean write) {
        if (actor == null || !actor.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        Set<String> roles = write ? Set.of("ROLE_STAFF_ADMIN", "ROLE_STAFF_OPERATOR", "ROLE_TEMP_TESTER")
                : Set.of("ROLE_STAFF_ADMIN", "ROLE_STAFF_OPERATOR", "ROLE_TEMP_TESTER", "ROLE_OWNER_READONLY");
        if (actor.getAuthorities().stream().noneMatch(authority -> roles.contains(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权处理此诊断请求");
        }
    }
    private void allowed(String value, Set<String> values) {
        if (value == null || !values.contains(value)) invalid("诊断字段取值无效");
    }
    private void uuid(String value) {
        if (value != null && !value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) {
            invalid("关联编号格式无效");
        }
    }
    private String categoryLabel(String category) {
        return switch (category) {
            case "UNHANDLED_ERROR" -> "未处理异常";
            case "PAGE_CRASH" -> "页面错误";
            case "NETWORK_ERROR" -> "网络请求失败";
            case "API_ERROR" -> "服务请求失败";
            default -> "用户主动报告";
        };
    }
    private String fingerprint(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("事件指纹算法不可用");
        }
    }
    public record ClientEvent(Instant occurredAt, String environment, String appVersion, String browserFamily, String category,
            String severity, String module, String routeTemplate, String requestId, String traceId,
            String relatedDiagnosticId, String summary, String userDescription) {}
    public record Receipt(UUID diagnosticId, Instant receivedAt) {}
    public record Page(List<DiagnosticIncident> items, int page, int size, long total) {}
    public record Detail(DiagnosticIncident incident, List<DiagnosticIncident.History> history) {}

    private void invalid(String reason) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
}
