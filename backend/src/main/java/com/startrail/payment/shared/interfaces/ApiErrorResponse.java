package com.startrail.payment.shared.interfaces;

import java.time.Instant;

public record ApiErrorResponse(String code, String message, Instant timestamp) {
}
