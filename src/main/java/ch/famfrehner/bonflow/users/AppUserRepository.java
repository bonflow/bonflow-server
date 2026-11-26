package ch.famfrehner.bonflow.users;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    List<AppUser> findAllByActiveTrueOrderByDisplayNameAsc();

    List<AppUser> findAllByOrderByDisplayNameAsc();
}
