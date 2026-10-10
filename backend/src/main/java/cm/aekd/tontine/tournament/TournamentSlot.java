package cm.aekd.tontine.tournament;

import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.session.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Passage d'un membre dans l'ordre d'un tournoi. {@code generatedAt} est fixé
 * quand la séance du passage a été créée ; la séance peut ensuite être
 * supprimée (session devient null) sans être régénérée.
 */
@Entity
@Table(name = "tournament_slot")
public class TournamentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "position", nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    @Column(name = "generated_at")
    private Instant generatedAt;

    protected TournamentSlot() {
    }

    public TournamentSlot(Tournament tournament, int position, Member member) {
        this.tournament = tournament;
        this.position = position;
        this.member = member;
    }

    public void markGenerated(Session session) {
        this.session = session;
        this.generatedAt = Instant.now();
    }

    public boolean isGenerated() {
        return generatedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public int getPosition() {
        return position;
    }

    public Member getMember() {
        return member;
    }

    public Session getSession() {
        return session;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TournamentSlot that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
