package cm.aekd.tontine.tournament;

import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Calendrier de séances générées automatiquement (docs/decisions.md n° 43) :
 * chaque mois, le membre suivant de l'ordre de passage reçoit la séance
 * (récepteur et bénéficiaire), à la date donnée par la règle « n-ième jour de
 * semaine du mois ».
 */
@Entity
@Table(name = "tournament")
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Toujours le 1er du mois. */
    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(name = "week_of_month", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int weekOfMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "lead_days", nullable = false)
    private int leadDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TournamentStatus status = TournamentStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TournamentSlot> slots = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "tournament_contribution",
            joinColumns = @JoinColumn(name = "tournament_id"),
            inverseJoinColumns = @JoinColumn(name = "contribution_definition_id"))
    private Set<ContributionDefinition> contributions = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tournament() {
    }

    public Tournament(String name, YearMonth startMonth, int weekOfMonth, DayOfWeek dayOfWeek, int leadDays,
                      User createdBy) {
        this.name = name;
        this.startMonth = startMonth.atDay(1);
        this.weekOfMonth = weekOfMonth;
        this.dayOfWeek = dayOfWeek;
        this.leadDays = leadDays;
        this.createdBy = createdBy;
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

    public void addSlot(TournamentSlot slot) {
        slots.add(slot);
    }

    /** Date prévue de la séance d'un passage. */
    /** « Le 1er samedi du mois », « Le dernier dimanche du mois ». */
    public String ruleLabel() {
        String rank = switch (weekOfMonth) {
            case 1 -> "1er";
            case -1 -> "dernier";
            default -> weekOfMonth + "e";
        };
        return "Le " + rank + " " + dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)
                + " du mois";
    }

    public LocalDate plannedDate(TournamentSlot slot) {
        return TournamentCalendar.dateFor(getStartMonth(), slot.getPosition(), weekOfMonth, dayOfWeek);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public YearMonth getStartMonth() {
        return YearMonth.from(startMonth);
    }

    public int getWeekOfMonth() {
        return weekOfMonth;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public int getLeadDays() {
        return leadDays;
    }

    public void setLeadDays(int leadDays) {
        this.leadDays = leadDays;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public void setStatus(TournamentStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public List<TournamentSlot> getSlots() {
        return slots;
    }

    public Set<ContributionDefinition> getContributions() {
        return contributions;
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
        if (!(o instanceof Tournament that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
