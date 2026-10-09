package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperationsPaymentActionService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/transactions")
public class OperationsPaymentActionController {
    private final OperationsPaymentActionService actionService;

    public OperationsPaymentActionController(OperationsPaymentActionService actionService) {
        this.actionService = actionService;
    }

    @PostMapping("/{paymentOrderId}/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refresh(Authentication authentication, @PathVariable UUID paymentOrderId) {
        actionService.refresh(authentication.getName(), paymentOrderId);
    }

    @PostMapping("/{paymentOrderId}/retry-notification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(Authentication authentication, @PathVariable UUID paymentOrderId) {
        actionService.retryNotification(authentication.getName(), paymentOrderId);
    }

    @PostMapping("/{paymentOrderId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public void addNote(Authentication authentication, @PathVariable UUID paymentOrderId,
            @RequestBody NoteRequest request) {
        actionService.addNote(authentication.getName(), paymentOrderId, request.body());
    }

    public record NoteRequest(String body) {
    }
}
