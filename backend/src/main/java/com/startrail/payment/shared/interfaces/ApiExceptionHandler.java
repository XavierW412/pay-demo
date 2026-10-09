package com.startrail.payment.shared.interfaces;

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
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidMockPaymentEventException.class)
    public ResponseEntity<ApiErrorResponse> handleMockPaymentEvent(InvalidMockPaymentEventException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_MOCK_PAYMENT_EVENT", exception.getMessage());
    }

    @ExceptionHandler(InvalidAirwallexWebhookException.class)
    public ResponseEntity<ApiErrorResponse> handleAirwallexWebhook(InvalidAirwallexWebhookException exception) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_AIRWALLEX_WEBHOOK", exception.getMessage());
    }

    @ExceptionHandler(InvalidIntegrationSignatureException.class)
    public ResponseEntity<ApiErrorResponse> handleSignature(InvalidIntegrationSignatureException exception) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_INTEGRATION_SIGNATURE", exception.getMessage());
    }

    @ExceptionHandler(InvalidPaymentOrderException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidOrder(InvalidPaymentOrderException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_ORDER", exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestBodyException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(InvalidRequestBodyException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", exception.getMessage());
    }

    @ExceptionHandler(PaymentOrderConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(PaymentOrderConflictException exception) {
        return response(HttpStatus.CONFLICT, "PAYMENT_ORDER_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(CheckoutContextNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingCheckout(CheckoutContextNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "CHECKOUT_CONTEXT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AirwallexConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderConfiguration(AirwallexConfigurationException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED", exception.getMessage());
    }

    @ExceptionHandler(AirwallexPaymentIntentException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderResponse(AirwallexPaymentIntentException exception) {
        return response(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_RESPONSE_INVALID", exception.getMessage());
    }

    @ExceptionHandler(MockProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMockProductNotFound(MockProductNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "MOCK_PRODUCT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(MockBusinessOrderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMockOrderNotFound(MockBusinessOrderNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "MOCK_ORDER_NOT_FOUND", exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, Instant.now()));
    }
}
