package com.startrail.payment.operations.security;

import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class OperatorUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbcTemplate;

    public OperatorUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return jdbcTemplate.query("""
                SELECT username, password_hash, role, active FROM operator_account
                WHERE username = ?
                """, (rs, rowNum) -> User.withUsername(rs.getString("username"))
                .password(rs.getString("password_hash"))
                .authorities("ROLE_" + rs.getString("role"))
                .disabled(!rs.getBoolean("active"))
                .build(), normalized).stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("运营账号不存在"));
    }
}
