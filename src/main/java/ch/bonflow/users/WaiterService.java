package ch.bonflow.users;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Waiter management and PIN authentication.
 *
 * <p>PINs are short, so they are protected by a per-waiter lockout: after {@value #MAX_FAILURES}
 * wrong PINs in a row the waiter is locked for {@link #LOCK_DURATION}. The counters live in memory,
 * which is sufficient because there is exactly one server instance per venue.
 */
@Service
public class WaiterService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(1);
    private static final Pattern PIN_FORMAT = Pattern.compile("\\d{4,8}");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Map<Long, Attempts> attempts = new ConcurrentHashMap<>();
    /** Compared against for unknown ids, so timing does not reveal which waiters exist. */
    private final String dummyHash;

    WaiterService(AppUserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = encoder.encode("0000");
    }

    @Transactional(readOnly = true)
    public List<WaiterInfo> activeWaiters() {
        return users.findAllByActiveTrueOrderByDisplayNameAsc().stream().map(WaiterInfo::of).toList();
    }

    /** Name of a waiter, also when deactivated since (printed on tickets of old orders). */
    @Transactional(readOnly = true)
    public Optional<String> displayName(long id) {
        return users.findById(id).map(AppUser::getDisplayName);
    }

    @Transactional(readOnly = true)
    public Optional<WaiterInfo> findActive(long id) {
        return users.findById(id).filter(AppUser::isActive).map(WaiterInfo::of);
    }

    /**
     * @return the waiter if id and PIN match and the waiter is active, otherwise empty
     * @throws TooManyAttemptsException while the waiter is locked
     */
    @Transactional(readOnly = true)
    public Optional<WaiterInfo> authenticate(long id, String pin) {
        Instant now = clock.instant();
        Attempts current = attempts.get(id);
        if (current != null && current.lockedUntil() != null && now.isBefore(current.lockedUntil())) {
            throw new TooManyAttemptsException(Duration.between(now, current.lockedUntil()));
        }
        Optional<AppUser> user = users.findById(id).filter(AppUser::isActive);
        boolean ok = encoder.matches(pin == null ? "" : pin, user.map(AppUser::getPinHash).orElse(dummyHash))
                && user.isPresent();
        if (ok) {
            attempts.remove(id);
            return user.map(WaiterInfo::of);
        }
        attempts.compute(id, (k, old) -> {
            // a finished lock starts a fresh count
            int failures = (old == null || old.lockedUntil() != null) ? 1 : old.failures() + 1;
            return failures >= MAX_FAILURES
                    ? new Attempts(failures, now.plus(LOCK_DURATION))
                    : new Attempts(failures, null);
        });
        return Optional.empty();
    }

    /**
     * Creates or updates a waiter.
     *
     * @param rawPin new PIN (4 to 8 digits); required for a new waiter, {@code null} or blank keeps the old one
     * @throws IllegalArgumentException with a user presentable message on invalid input
     */
    @Transactional
    public AppUser save(AppUser user, String rawPin) {
        boolean hasPin = rawPin != null && !rawPin.isBlank();
        if (user.isNew() && !hasPin) {
            throw new IllegalArgumentException("Für neue Mitarbeitende ist eine PIN erforderlich.");
        }
        if (hasPin) {
            if (!PIN_FORMAT.matcher(rawPin).matches()) {
                throw new IllegalArgumentException("Die PIN muss aus 4 bis 8 Ziffern bestehen.");
            }
            user.setPinHash(encoder.encode(rawPin));
            attempts.remove(user.getId() == null ? -1L : user.getId());
        }
        return users.save(user);
    }

    private record Attempts(int failures, Instant lockedUntil) {
    }
}
