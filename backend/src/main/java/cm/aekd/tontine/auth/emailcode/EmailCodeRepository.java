package cm.aekd.tontine.auth.emailcode;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailCodeRepository extends JpaRepository<EmailCode, UUID> {

    Optional<EmailCode> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, EmailCode.Purpose purpose);

    long countByEmailAndCreatedAtAfter(String email, Instant since);

    /** Un nouveau code annule les précédents encore valables, pour cette adresse et cet usage. */
    @Modifying
    @Query("UPDATE EmailCode c SET c.consumedAt = :now WHERE c.email = :email AND c.purpose = :purpose "
            + "AND c.consumedAt IS NULL")
    void consumeAllActive(@Param("email") String email, @Param("purpose") EmailCode.Purpose purpose,
                          @Param("now") Instant now);
}
