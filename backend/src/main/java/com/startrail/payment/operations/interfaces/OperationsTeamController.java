package com.startrail.payment.operations.interfaces;

import com.startrail.payment.operations.application.OperationsTeamService;
import com.startrail.payment.operations.application.TemporaryOperatorAccountService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/team")
public class OperationsTeamController {
    private final OperationsTeamService teamService;
    private final TemporaryOperatorAccountService temporaryOperatorAccountService;

    public OperationsTeamController(
            OperationsTeamService teamService,
            TemporaryOperatorAccountService temporaryOperatorAccountService) {
        this.teamService = teamService;
        this.temporaryOperatorAccountService = temporaryOperatorAccountService;
    }

    @GetMapping("/accounts")
    public OperationsTeamService.TeamPage<OperationsTeamService.AccountRow> accounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return teamService.accounts(page, size);
    }

    @GetMapping("/invitations")
    public OperationsTeamService.TeamPage<OperationsTeamService.InvitationRow> invitations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return teamService.invitations(page, size);
    }

    @DeleteMapping("/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(Authentication authentication, @PathVariable UUID invitationId) {
        teamService.revokeInvitation(authentication.getName(), invitationId);
    }

    @PatchMapping("/accounts/{accountId}/active")
    public OperationsTeamService.AccountRow setAccountActive(
            Authentication authentication,
            @PathVariable UUID accountId,
            @RequestBody ActiveRequest request) {
        return teamService.setAccountActive(authentication.getName(), accountId, request.active());
    }

    @PostMapping("/temporary-accounts")
    public ResponseEntity<TemporaryOperatorAccountService.TemporaryOperatorCredentials> createTemporaryAccount(
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(temporaryOperatorAccountService.create(authentication.getName()));
    }

    public record ActiveRequest(boolean active) {}
}
