package ch.famfrehner.bonflow.api;

import java.util.List;

import ch.famfrehner.bonflow.security.TokenService;
import ch.famfrehner.bonflow.users.TooManyAttemptsException;
import ch.famfrehner.bonflow.users.WaiterInfo;
import ch.famfrehner.bonflow.users.WaiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Waiter login on shared phones: pick the name from the list, enter the PIN. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController {

    private static final List<String> WAITER_ROLES = List.of("WAITER");

    private final WaiterService waiters;
    private final TokenService tokens;

    AuthController(WaiterService waiters, TokenService tokens) {
        this.waiters = waiters;
        this.tokens = tokens;
    }

    public record LoginRequest(@NotNull Long waiterId, @NotBlank String pin) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType,
            long expiresInSeconds, WaiterInfo waiter) {
    }

    @Operation(summary = "Active waiters to choose from on the login screen")
    @GetMapping("/waiters")
    List<WaiterInfo> waiters() {
        return waiters.activeWaiters();
    }

    @Operation(summary = "Log in with waiter id and PIN")
    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            WaiterInfo waiter = waiters.authenticate(request.waiterId(), request.pin())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid PIN"));
            return ResponseEntity.ok(respond(waiter));
        } catch (TooManyAttemptsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, e.getRetryAfter().toSeconds())))
                    .build();
        }
    }

    @Operation(summary = "Exchange a refresh token for a new token pair")
    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        WaiterInfo waiter = tokens.subjectOfRefreshToken(request.refreshToken())
                .flatMap(waiters::findActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        return respond(waiter);
    }

    private TokenResponse respond(WaiterInfo waiter) {
        TokenService.TokenPair pair = tokens.issue(waiter.id(), waiter.displayName(), WAITER_ROLES);
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer",
                pair.expiresInSeconds(), waiter);
    }
}
