package ch.bonflow.users;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Management of web UI accounts (admins, managers, station devices) and their passwords. */
@Service
public class AdminUserService {

    static final int MIN_PASSWORD_LENGTH = 8;
    private static final Set<String> ROLES = Set.of(AdminUser.ADMIN, AdminUser.MANAGER, AdminUser.STATION);

    private final AdminUserRepository users;
    private final PasswordEncoder encoder;

    AdminUserService(AdminUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUser> all() {
        return users.findAllByOrderByUsernameAsc();
    }

    /** The account as Spring Security needs it, or empty if the username is unknown. */
    @Transactional(readOnly = true)
    public Optional<UserDetails> findUserDetails(String username) {
        return users.findByUsername(username).map(u -> User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles(u.getRole())
                .build());
    }

    /**
     * Creates or updates an account.
     *
     * @param rawPassword new password; required for a new account, {@code null} or blank keeps the old one
     * @throws IllegalArgumentException with a user presentable message on invalid input
     */
    @Transactional
    public AdminUser save(AdminUser user, String rawPassword) {
        if (!ROLES.contains(user.getRole())) {
            throw new IllegalArgumentException("Unbekannte Rolle.");
        }
        boolean hasPassword = rawPassword != null && !rawPassword.isBlank();
        if (user.isNew() && !hasPassword) {
            throw new IllegalArgumentException("Für neue Konten ist ein Passwort erforderlich.");
        }
        if (hasPassword) {
            if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalArgumentException(
                        "Das Passwort muss mindestens " + MIN_PASSWORD_LENGTH + " Zeichen lang sein.");
            }
            user.setPasswordHash(encoder.encode(rawPassword));
        }
        if (!user.isNew()) {
            AdminUser stored = users.findById(user.getId()).orElseThrow();
            if (AdminUser.ADMIN.equals(stored.getRole()) && !AdminUser.ADMIN.equals(user.getRole())
                    && users.countByRole(AdminUser.ADMIN) <= 1) {
                throw new IllegalArgumentException("Das letzte Admin-Konto kann nicht herabgestuft werden.");
            }
        }
        return users.save(user);
    }

    /**
     * Self-service password change of a signed-in user.
     *
     * @throws IllegalArgumentException with a user presentable message when the current password is
     *     wrong, the new one is too short, or the account only exists in the configuration (bootstrap)
     */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AdminUser user = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException(
                "Dieses Konto wird über die Konfiguration verwaltet. Lege unter Konten ein eigenes Konto an."));
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Das aktuelle Passwort ist falsch.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Das neue Passwort muss mindestens " + MIN_PASSWORD_LENGTH + " Zeichen lang sein.");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
    }

    /** @throws IllegalArgumentException when the account is the last administrator */
    @Transactional
    public void delete(AdminUser user) {
        if (AdminUser.ADMIN.equals(user.getRole()) && users.countByRole(AdminUser.ADMIN) <= 1) {
            throw new IllegalArgumentException("Das letzte Admin-Konto kann nicht gelöscht werden.");
        }
        users.deleteById(user.getId());
    }
}
