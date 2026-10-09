package com.startrail.payment.operations.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class OperationsSecurityConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                        "/api/v1/integration/**",
                        "/api/v1/webhooks/**",
                        "/api/v1/checkout/*/refresh",
                        "/mock-xingzhe/api/**"));
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/operations/csrf", "/api/v1/operations/login",
                        "/api/v1/operations/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/invitations").hasAuthority("ROLE_STAFF_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/refunds/*/confirm")
                    .hasAuthority("ROLE_STAFF_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/operations/transactions/**")
                    .hasAnyAuthority("ROLE_STAFF_ADMIN", "ROLE_STAFF_OPERATOR")
                .requestMatchers("/api/v1/operations/**").authenticated()
                .anyRequest().permitAll());
        http.formLogin(form -> form
                .loginProcessingUrl("/api/v1/operations/login")
                .successHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .failureHandler((request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)));
        http.logout(logout -> logout
                .logoutUrl("/api/v1/operations/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT)));
        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request, response, exception) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)));
        return http.build();
    }
}
