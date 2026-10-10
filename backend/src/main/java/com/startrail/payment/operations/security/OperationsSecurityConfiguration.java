package com.startrail.payment.operations.security;

import com.startrail.payment.observability.infrastructure.SafeErrorLogContext;
import com.startrail.payment.shared.interfaces.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class OperationsSecurityConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationsSecurityConfiguration.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                        "/api/v1/integration/**",
                        "/api/v1/webhooks/**",
                        "/api/v1/checkout/*/refresh",
                        "/api/v1/checkout/return/*/refresh",
                        "/mock-xingzhe/api/**"));
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/operations/csrf", "/api/v1/operations/login",
                        "/api/v1/operations/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/invitations").hasAuthority("ROLE_STAFF_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/team/temporary-accounts")
                    .hasAuthority("ROLE_STAFF_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/operations/team/invitations/**").hasAuthority("ROLE_STAFF_ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/operations/team/accounts/**").hasAuthority("ROLE_STAFF_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/refunds/*/confirm")
                    .hasAnyAuthority("ROLE_STAFF_ADMIN", "ROLE_TEMP_TESTER")
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/refunds/*/refresh")
                    .hasAnyAuthority("ROLE_STAFF_ADMIN", "ROLE_STAFF_OPERATOR", "ROLE_TEMP_TESTER")
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/transactions/**")
                    .hasAnyAuthority("ROLE_STAFF_ADMIN", "ROLE_STAFF_OPERATOR", "ROLE_TEMP_TESTER")
                .requestMatchers("/api/v1/operations/**").authenticated()
                .anyRequest().permitAll());
        http.formLogin(form -> form
                .loginProcessingUrl("/api/v1/operations/login")
                .successHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .failureHandler((request, response, exception) -> writeSecurityError(response, objectMapper,
                        HttpServletResponse.SC_UNAUTHORIZED, "AUTHENTICATION_FAILED")));
        http.logout(logout -> logout
                .logoutUrl("/api/v1/operations/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT)));
        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, exception) -> writeSecurityError(response, objectMapper,
                        HttpServletResponse.SC_UNAUTHORIZED, "AUTHENTICATION_REQUIRED"))
                .accessDeniedHandler((request, response, exception) -> writeSecurityError(response, objectMapper,
                        HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED")));
        http.addFilterAfter(new OperatorAccountStatusFilter(jdbcTemplate, objectMapper),
                SecurityContextHolderFilter.class);
        return http.build();
    }

    private static void writeSecurityError(HttpServletResponse response, ObjectMapper objectMapper,
            int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        SafeErrorLogContext.record(LOGGER, code, status, null);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(code, "请求未获授权", Instant.now()));
    }
}
