package cm.aekd.tontine.session;

import cm.aekd.tontine.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Membre bénéficiaire d'une séance (celui qui reçoit la cagnotte de la
 * séance). Concept distinct de ContributionBeneficiary (bénéficiaire d'une
 * cotisation, CLAUDE.md §12) : les deux ne doivent pas être confondus.
 */
@Entity
@Table(name = "session_beneficiary")
public class SessionBeneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SessionBeneficiary() {
    }

    public SessionBeneficiary(Session session, Member member) {
        this.session = session;
        this.member = member;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Session getSession() {
        return session;
    }

    public Member getMember() {
        return member;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionBeneficiary that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
