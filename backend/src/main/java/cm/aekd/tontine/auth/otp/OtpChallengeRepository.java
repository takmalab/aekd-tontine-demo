package cm.aekd.tontine.auth.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    Optional<OtpChallenge> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);

    /** Un nouveau code annule les précédents encore valables pour ce numéro. */
    @Modifying
    @Query("UPDATE OtpChallenge c SET c.consumedAt = :now WHERE c.phone = :phone AND c.consumedAt IS NULL")
    void consumeAllActive(@Param("phone") String phone, @Param("now") Instant now);
}
