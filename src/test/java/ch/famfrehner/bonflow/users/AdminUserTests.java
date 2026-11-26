package ch.famfrehner.bonflow.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import ch.famfrehner.bonflow.TestcontainersConfiguration;

@SpringBootTest(properties = {
        "bonflow.security.jwt-secret=test-secret-test-secret-test-secret-0123",
        "bonflow.security.bootstrap-admin-password=test"
})
@Import(TestcontainersConfiguration.class)
class AdminUserTests {

    @Autowired
    AdminUserService service;

    @Autowired
    AdminUserRepository repository;

    @Autowired
    UserDetailsService accounts;

    private AdminUser account(String username, String role) {
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    @Test
    void databaseAccountCanLogInWithItsRole() {
        service.save(account("kueche-1", AdminUser.STATION), "geheim-1234");

        UserDetails details = accounts.loadUserByUsername("kueche-1");

        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_STATION");
        assertThat(details.getPassword()).isNotEqualTo("geheim-1234").startsWith("$2");
    }

    @Test
    void bootstrapAccountStillWorksAndUnknownIsRejected() {
        assertThat(accounts.loadUserByUsername("admin").getUsername()).isEqualTo("admin");
        assertThatThrownBy(() -> accounts.loadUserByUsername("niemand"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void passwordIsRequiredAndMustBeLongEnough() {
        assertThatThrownBy(() -> service.save(account("ohne", AdminUser.MANAGER), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(account("kurz", AdminUser.MANAGER), "1234567"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lastAdminCannotBeDeletedOrDemoted() {
        repository.deleteAll();
        AdminUser only = service.save(account("chef", AdminUser.ADMIN), "passwort-1");

        assertThatThrownBy(() -> service.delete(only)).isInstanceOf(IllegalArgumentException.class);
        only.setRole(AdminUser.MANAGER);
        assertThatThrownBy(() -> service.save(only, null)).isInstanceOf(IllegalArgumentException.class);

        AdminUser second = service.save(account("chef2", AdminUser.ADMIN), "passwort-2");
        service.delete(second);
        assertThat(repository.findByUsername("chef2")).isEmpty();
    }
}
