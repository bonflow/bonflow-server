package ch.famfrehner.bonflow.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A waiter. Logs in on a shared phone by picking the name and entering a PIN. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 100, unique = true)
    private String login;

    @Column(name = "pin_hash", nullable = false)
    private String pinHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, length = 20)
    private String role = "WAITER";

    public Long getId() {
        return id;
    }

    public boolean isNew() {
        return id == null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    String getPinHash() {
        return pinHash;
    }

    void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getRole() {
        return role;
    }
}
