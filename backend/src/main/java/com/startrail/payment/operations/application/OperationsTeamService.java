package com.startrail.payment.operations.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class OperationsTeamService {
    private final JdbcTemplate jdbcTemplate;
    private final OperatorAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public OperationsTeamService(
            JdbcTemplate jdbcTemplate,
            OperatorAuditService auditService,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TeamPage<AccountRow> accounts(int page, int size) {
        validatePage(page, size);
        var total = count("SELECT count(*) FROM operator_account");
        var items = jdbcTemplate.query("""
                SELECT id, username, role, active, created_at
                FROM operator_account ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new AccountRow(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getString("role"), rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant()),
                size, (long) page * size);
        return new TeamPage<>(items, page, size, total);
    }

    public TeamPage<InvitationRow> invitations(int page, int size) {
        validatePage(page, size);
        var total = count("SELECT count(*) FROM operator_invitation");
        var items = jdbcTemplate.query("""
                SELECT i.id, i.issued_by, a.username AS issued_by_username, i.expires_at,
                       i.used_at, i.revoked_at, i.created_at
                FROM operator_invitation i
                JOIN operator_account a ON a.id = i.issued_by
                ORDER BY i.created_at DESC, i.id DESC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new InvitationRow(rs.getObject("id", UUID.class),
                rs.getString("issued_by_username"), invitationStatus(rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("used_at"), rs.getTimestamp("revoked_at")),
                rs.getTimestamp("expires_at").toInstant(), instant(rs.getTimestamp("used_at")),
                instant(rs.getTimestamp("revoked_at")), rs.getTimestamp("created_at").toInstant()),
                size, (long) page * size);
        return new TeamPage<>(items, page, size, total);
    }

    public void revokeInvitation(String actorUsername, UUID invitationId) {
        requireAdmin(actorUsername);
        try {
            transactionTemplate.execute(status -> {
                var changed = jdbcTemplate.update("""
                        UPDATE operator_invitation
                        SET revoked_at = now()
                        WHERE id = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()
                        """, invitationId);
                if (changed == 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "邀请不存在、已使用、已撤销或已过期");
                }
                auditService.record(actorUsername, null, "REVOKE_OPERATOR_INVITATION", "SUCCEEDED",
                        "撤销邀请：" + invitationId);
                return null;
            });
        } catch (ResponseStatusException exception) {
            recordRejected(actorUsername, "REVOKE_OPERATOR_INVITATION",
                    "撤销邀请失败：" + invitationId + "；" + reason(exception));
            throw exception;
        } catch (RuntimeException exception) {
            recordRejected(actorUsername, "REVOKE_OPERATOR_INVITATION",
                    "撤销邀请执行失败：" + invitationId, "FAILED");
            throw exception;
        }
    }

    public AccountRow setAccountActive(String actorUsername, UUID accountId, boolean active) {
        requireAdmin(actorUsername);
        var action = active ? "RESTORE_OPERATOR" : "DISABLE_OPERATOR";
        try {
            return transactionTemplate.execute(status -> {
                var actor = normalize(actorUsername);
                var lockedAdmins = jdbcTemplate.query("""
                        SELECT id FROM operator_account
                        WHERE role = 'STAFF_ADMIN' AND active = true
                        ORDER BY id FOR UPDATE
                        """, (rs, rowNum) -> rs.getObject("id", UUID.class));
                if (lockedAdmins.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "系统当前没有启用的员工管理员");
                }

                var target = jdbcTemplate.query("""
                        SELECT id, username, role, active, created_at
                        FROM operator_account WHERE id = ? FOR UPDATE
                        """, (rs, rowNum) -> new AccountRow(rs.getObject("id", UUID.class),
                        rs.getString("username"), rs.getString("role"), rs.getBoolean("active"),
                        rs.getTimestamp("created_at").toInstant()), accountId)
                        .stream().findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "员工账号不存在"));
                if (target.username().equalsIgnoreCase(actor)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "不能停用或恢复当前登录账号");
                }
                if (!"STAFF_OPERATOR".equals(target.role())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员只能管理普通员工账号");
                }
                if (target.active() == active) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            active ? "该员工账号已经启用" : "该员工账号已经停用");
                }
                jdbcTemplate.update("UPDATE operator_account SET active = ? WHERE id = ?", active, accountId);
                auditService.record(actorUsername, null, action, "SUCCEEDED",
                        (active ? "恢复员工账号：" : "停用员工账号：") + target.username());
                return new AccountRow(target.id(), target.username(), target.role(), active, target.createdAt());
            });
        } catch (ResponseStatusException exception) {
            recordRejected(actorUsername, action,
                    (active ? "恢复员工账号失败：" : "停用员工账号失败：") + accountId + "；" + reason(exception));
            throw exception;
        } catch (RuntimeException exception) {
            recordRejected(actorUsername, action,
                    (active ? "恢复员工账号执行失败：" : "停用员工账号执行失败：") + accountId, "FAILED");
            throw exception;
        }
    }

    private void recordRejected(String actorUsername, String action, String detail) {
        recordRejected(actorUsername, action, detail, "REJECTED");
    }

    private void recordRejected(String actorUsername, String action, String detail, String outcome) {
        auditService.record(actorUsername, null, action, outcome, detail);
    }

    private String reason(ResponseStatusException exception) {
        return exception.getReason() == null ? "业务规则未满足" : exception.getReason();
    }

    private void requireAdmin(String actorUsername) {
        var role = jdbcTemplate.query("SELECT role FROM operator_account WHERE username = ? AND active = true",
                (rs, rowNum) -> rs.getString("role"), normalize(actorUsername)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "运营账号已停用或不存在"));
        if (!"STAFF_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅员工管理员可执行此操作");
        }
    }

    private long count(String sql) {
        var count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private String invitationStatus(Instant expiresAt, java.sql.Timestamp usedAt, java.sql.Timestamp revokedAt) {
        if (usedAt != null) return "USED";
        if (revokedAt != null) return "REVOKED";
        if (!expiresAt.isAfter(Instant.now())) return "EXPIRED";
        return "PENDING";
    }

    private Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new InvalidOperatorRequestException("分页参数无效");
    }

    public record AccountRow(UUID id, String username, String role, boolean active, Instant createdAt) {}

    public record InvitationRow(UUID id, String issuedBy, String status, Instant expiresAt,
            Instant usedAt, Instant revokedAt, Instant createdAt) {}

    public record TeamPage<T>(List<T> items, int page, int size, long total) {}
}
