package com.startrail.payment.shared.interfaces;

import com.startrail.payment.observability.infrastructure.SafeErrorLogContext;
import com.startrail.payment.integrations.startrail.InvalidIntegrationSignatureException;
import com.startrail.payment.checkout.application.CheckoutContextNotFoundException;
import com.startrail.payment.integrations.airwallex.auth.AirwallexConfigurationException;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentException;
import com.startrail.payment.integrations.airwallex.webhook.InvalidAirwallexWebhookException;
import com.startrail.payment.payment.domain.InvalidPaymentOrderException;
import com.startrail.payment.payment.domain.PaymentOrderConflictException;
import com.startrail.payment.payment.interfaces.InvalidRequestBodyException;
import com.startrail.payment.simulator.application.MockBusinessOrderNotFoundException;
import com.startrail.payment.simulator.application.MockProductNotFoundException;
import com.startrail.payment.simulator.application.InvalidMockPaymentEventException;
import com.startrail.payment.operations.application.InvalidOperatorRequestException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(InvalidOperatorRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidOperatorRequest(InvalidOperatorRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_OPERATOR_REQUEST", exception.getMessage(), exception);
    }
    @ExceptionHandler(InvalidMockPaymentEventException.class)
    public ResponseEntity<ApiErrorResponse> handleMockPaymentEvent(InvalidMockPaymentEventException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_MOCK_PAYMENT_EVENT", exception.getMessage(), exception);
    }

    @ExceptionHandler(InvalidAirwallexWebhookException.class)
    public ResponseEntity<ApiErrorResponse> handleAirwallexWebhook(InvalidAirwallexWebhookException exception) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_AIRWALLEX_WEBHOOK", exception.getMessage(), exception);
    }

    @ExceptionHandler(InvalidIntegrationSignatureException.class)
    public ResponseEntity<ApiErrorResponse> handleSignature(InvalidIntegrationSignatureException exception) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_INTEGRATION_SIGNATURE", exception.getMessage(), exception);
    }

    @ExceptionHandler(InvalidPaymentOrderException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidOrder(InvalidPaymentOrderException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_ORDER", exception.getMessage(), exception);
    }

    @ExceptionHandler(InvalidRequestBodyException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(InvalidRequestBodyException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", exception.getMessage(), exception);
    }

    @ExceptionHandler(PaymentOrderConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(PaymentOrderConflictException exception) {
        return response(HttpStatus.CONFLICT, "PAYMENT_ORDER_CONFLICT", exception.getMessage(), exception);
    }

    @ExceptionHandler(CheckoutContextNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingCheckout(CheckoutContextNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "CHECKOUT_CONTEXT_NOT_FOUND", exception.getMessage(), exception);
    }

    @ExceptionHandler(AirwallexConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderConfiguration(AirwallexConfigurationException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED", exception.getMessage(), exception);
    }

    @ExceptionHandler(AirwallexPaymentIntentException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderResponse(AirwallexPaymentIntentException exception) {
        return response(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_RESPONSE_INVALID", exception.getMessage(), exception);
    }

    @ExceptionHandler(MockProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMockProductNotFound(MockProductNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "MOCK_PRODUCT_NOT_FOUND", exception.getMessage(), exception);
    }

    @ExceptionHandler(MockBusinessOrderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMockOrderNotFound(MockBusinessOrderNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "MOCK_ORDER_NOT_FOUND", exception.getMessage(), exception);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String message = status >= 500 || exception.getReason() == null
                ? "请求无法处理" : exception.getReason();
        return response(status, "HTTP_" + status, message, exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            return response(status, "HTTP_" + status, "请求无法处理", exception);
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "服务暂时不可用，请稍后重试", exception);
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message,
            Exception exception) {
        return response(status.value(), code, message, exception);
    }

    private ResponseEntity<ApiErrorResponse> response(int status, String code, String message,
            Exception exception) {
        SafeErrorLogContext.record(LOGGER, exception.getClass().getSimpleName(), status, exception);
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, Instant.now()));
    }
}
