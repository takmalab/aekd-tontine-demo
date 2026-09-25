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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Séance : réunion de la tontine tenue un jour donné (CLAUDE.md §7, adapté
 * sur décision du porteur du projet : une date unique au lieu d'une période),
 * avec un lieu et un membre récepteur. Les cotisations y sont rattachées via
 * ContributionPeriod ; ses bénéficiaires sont dans SessionBeneficiary.
 */
@Entity
@Table(name = "session")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    /** Nullable en base pour les séances antérieures à la migration V12. */
    @Column(name = "location", length = 150)
    private String location;

    /** Membre récepteur ; nullable en base pour les séances antérieures à V12. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_member_id")
    private Member hostMember;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Session() {
    }

    public Session(String label, LocalDate sessionDate, String location, Member hostMember) {
        this.label = label;
        this.sessionDate = sessionDate;
        this.location = location;
        this.hostMember = hostMember;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Member getHostMember() {
        return hostMember;
    }

    public void setHostMember(Member hostMember) {
        this.hostMember = hostMember;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Session session)) return false;
        return id != null && id.equals(session.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
