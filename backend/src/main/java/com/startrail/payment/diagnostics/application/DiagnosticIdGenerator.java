package com.startrail.payment.diagnostics.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticIdGenerator {
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
