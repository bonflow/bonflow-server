package ch.bonflow.users;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);

    List<AdminUser> findAllByOrderByUsernameAsc();

    long countByRole(String role);
}
