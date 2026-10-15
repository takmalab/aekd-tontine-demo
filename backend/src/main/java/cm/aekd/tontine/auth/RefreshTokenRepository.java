package cm.aekd.tontine.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Ménage : jetons expirés ou révoqués d'un utilisateur. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId AND (t.expiresAt < :now OR t.revokedAt IS NOT NULL)")
    void deleteStaleForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
