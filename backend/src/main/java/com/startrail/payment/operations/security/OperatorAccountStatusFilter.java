package com.startrail.payment.operations.security;

import com.startrail.payment.shared.interfaces.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class OperatorAccountStatusFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OperatorAccountStatusFilter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return !path.startsWith(request.getContextPath() + "/api/v1/operations/")
                || path.equals(request.getContextPath() + "/api/v1/operations/login")
                || path.equals(request.getContextPath() + "/api/v1/operations/register")
                || path.equals(request.getContextPath() + "/api/v1/operations/csrf");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            chain.doFilter(request, response);
            return;
        }

        boolean active;
        try {
            active = jdbcTemplate.query("SELECT active FROM operator_account WHERE username = ?",
                    (rs, rowNum) -> rs.getBoolean("active"), authentication.getName())
                    .stream().findFirst().orElse(false);
        } catch (DataAccessException exception) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "OPERATOR_STATUS_UNAVAILABLE", "暂时无法核实运营账号状态，请稍后重试");
            return;
        }
        if (!active) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "OPERATOR_ACCOUNT_DISABLED", "运营账号已停用，请重新登录");
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(code, message, Instant.now()));
    }
}
