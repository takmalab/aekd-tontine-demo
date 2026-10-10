package cm.aekd.tontine.payout;

import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Remise d'une part de la cagnotte de la séance à l'un de ses bénéficiaires
 * (docs/decisions.md n° 46). Enregistrée par le trésorier ou l'administrateur
 * une fois l'argent remis : elle diminue le fonds commun restant à remettre.
 */
@Entity
@Table(name = "session_payout")
public class SessionPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(name = "payout_date", nullable = false)
    private LocalDate payoutDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 20)
    private PaymentOperator operator;

    @Column(name = "reference", length = 150)
    private String reference;

    @Column(name = "observation")
    private String observation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SessionPayout() {
    }

    public SessionPayout(Session session, Member member, BigDecimal amount, LocalDate payoutDate,
                         PaymentOperator operator, String reference, String observation, User recordedBy) {
        this.session = session;
        this.member = member;
        this.amount = amount;
        this.payoutDate = payoutDate;
        this.operator = operator;
        this.reference = reference;
        this.observation = observation;
        this.recordedBy = recordedBy;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPayoutDate() {
        return payoutDate;
    }

    public PaymentOperator getOperator() {
        return operator;
    }

    public String getReference() {
        return reference;
    }

    public String getObservation() {
        return observation;
    }

    public User getRecordedBy() {
        return recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionPayout that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
