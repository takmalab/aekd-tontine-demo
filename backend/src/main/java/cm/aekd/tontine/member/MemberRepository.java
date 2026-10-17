package cm.aekd.tontine.member;

import cm.aekd.tontine.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    /** Membres qui ont une photo de profil (T2 : l'interface ne demande que celles-là). */
    @Query("select m.id from Member m where m.user.photoStorageKey is not null")
    List<UUID> findIdsWithPhoto();

    Optional<Member> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    long countByActiveTrue();

    /** Membres actifs dont le compte a le statut donné (ex. destinataires des notifications). */
    @Query("SELECT m FROM Member m JOIN FETCH m.user u WHERE m.active = true AND u.status = :status")
    List<Member> findActiveByUserStatus(@Param("status") UserStatus status);
}
