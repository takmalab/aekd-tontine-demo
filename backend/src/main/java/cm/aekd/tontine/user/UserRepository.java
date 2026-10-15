package cm.aekd.tontine.user;

import cm.aekd.tontine.security.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    List<User> findByStatus(UserStatus status);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.name IN :roles AND u.status = :status")
    List<User> findByRoleNamesAndStatus(@Param("roles") Collection<RoleName> roles,
                                        @Param("status") UserStatus status);
}
