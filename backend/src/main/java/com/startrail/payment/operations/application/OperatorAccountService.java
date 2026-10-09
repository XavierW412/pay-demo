package com.startrail.payment.operations.application;

import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperatorAccountService {
    private final JdbcTemplate jdbcTemplate;

    public OperatorAccountService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID requireId(String username) {
        return jdbcTemplate.query("SELECT id FROM operator_account WHERE username = ? AND active = true",
                (rs, rowNum) -> rs.getObject("id", UUID.class), username.toLowerCase(Locale.ROOT))
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("运营账号不存在"));
    }
}
