package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperatorInvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperatorInvitationController {
    private final OperatorInvitationService invitationService;

    public OperatorInvitationController(OperatorInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse issue(Authentication authentication) {
        return new InvitationResponse(invitationService.issue(authentication.getName()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@RequestBody RegistrationRequest request) {
        invitationService.register(request.token(), request.username(), request.password());
    }

    public record InvitationResponse(String token) {
    }

    public record RegistrationRequest(String token, String username, String password) {
    }
}
