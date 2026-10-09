package com.startrail.payment.operations.interfaces;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsSessionController {
    @GetMapping("/csrf")
    public CsrfResponse csrf(HttpServletRequest request) {
        var csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            throw new IllegalStateException("无法生成运营会话校验令牌");
        }
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @GetMapping("/me")
    public SessionResponse me(Authentication authentication) {
        var role = authentication.getAuthorities().iterator().next().getAuthority().replaceFirst("^ROLE_", "");
        return new SessionResponse(authentication.getName(), role);
    }

    public record CsrfResponse(String token, String headerName) {
    }

    public record SessionResponse(String username, String role) {
    }
}
