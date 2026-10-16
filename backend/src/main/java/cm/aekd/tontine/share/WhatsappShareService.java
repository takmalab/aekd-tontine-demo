package cm.aekd.tontine.share;

import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionParticipant;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionPeriodStatus;
import cm.aekd.tontine.contribution.ContributionPeriodStatusService;
import cm.aekd.tontine.contribution.ContributionStatus;
import cm.aekd.tontine.document.AekdPdf;
import cm.aekd.tontine.document.DocumentFormat;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.payout.SessionPayoutDtos.BeneficiaryShare;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutOverview;
import cm.aekd.tontine.payout.SessionPayoutService;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionBeneficiary;
import cm.aekd.tontine.session.SessionBeneficiaryRepository;
import cm.aekd.tontine.session.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Messages prêts à partager sur WhatsApp (docs/decisions.md n° 59) : le bureau les
 * envoie lui-même dans le groupe de la tontine (aucun envoi automatique). Mise en
 * forme WhatsApp : *gras*, listes à puces.
 */
@Service
@Transactional(readOnly = true)
public class WhatsappShareService {

    public enum Kind { ANNOUNCEMENT, STATUS, PAYOUT }

    private final SessionRepository sessionRepository;
    private final SessionBeneficiaryRepository beneficiaryRepository;
    private final ContributionPeriodRepository periodRepository;
    private final ContributionParticipantRepository participantRepository;
    private final ContributionPeriodStatusService statusService;
    private final SessionPayoutService payoutService;
    private final cm.aekd.tontine.tournament.TournamentRepository tournamentRepository;
    private final String publicUrl;

    public WhatsappShareService(SessionRepository sessionRepository, SessionBeneficiaryRepository beneficiaryRepository,
                                ContributionPeriodRepository periodRepository,
                                ContributionParticipantRepository participantRepository,
                                ContributionPeriodStatusService statusService, SessionPayoutService payoutService,
                                cm.aekd.tontine.tournament.TournamentRepository tournamentRepository,
                                @Value("${app.public-url:}") String publicUrl) {
        this.tournamentRepository = tournamentRepository;
        this.sessionRepository = sessionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.periodRepository = periodRepository;
        this.participantRepository = participantRepository;
        this.statusService = statusService;
        this.payoutService = payoutService;
        this.publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
    }

    public String sessionMessage(UUID sessionId, Kind kind) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        String body = switch (kind) {
            case ANNOUNCEMENT -> announcement(session);
            case STATUS -> status(session);
            case PAYOUT -> payout(session);
        };
        // Lien vers la rubrique concernée de la séance (decisions.md n° 59).
        String sessionUrl = "/sessions/" + sessionId;
        String links = switch (kind) {
            case ANNOUNCEMENT -> link("Voir la séance", sessionUrl);
            case STATUS -> link("Voir les cotisations", sessionUrl + "#cotisations")
                    + link("Payer ma cotisation", "/my-contributions");
            case PAYOUT -> link("Voir la cagnotte", sessionUrl + "#cagnotte");
        };
        return body + (links.isEmpty() ? "" : "\n" + links) + signature();
    }

    /** Ordre de passage d'un tournoi. */
    public String tournamentMessage(UUID tournamentId) {
        cm.aekd.tontine.tournament.Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournoi introuvable"));
        StringBuilder text = new StringBuilder("🔁 *Ordre de passage – ").append(tournament.getName()).append("*\n")
                .append(tournament.ruleLabel()).append("\n\n");
        java.time.LocalDate today = java.time.LocalDate.now(DocumentFormat.ZONE);
        tournament.getSlots().stream()
                .sorted(Comparator.comparingInt(cm.aekd.tontine.tournament.TournamentSlot::getPosition))
                .forEach(slot -> {
                    java.time.LocalDate date = tournament.plannedDate(slot);
                    boolean past = slot.isGenerated() || (date != null && date.isBefore(today));
                    text.append(slot.getPosition() + 1).append(". ").append(slot.getMember().getFullName());
                    if (date != null) {
                        text.append(" – ").append(DocumentFormat.date(date));
                    }
                    text.append(past ? " ✅" : "").append('\n');
                });
        String links = link("Voir les séances", "/sessions");
        return text.toString().stripTrailing() + (links.isEmpty() ? "" : "\n" + links) + signature();
    }

    /** Ligne « 👉 libellé : adresse », vide si l'adresse publique n'est pas configurée. */
    private String link(String label, String path) {
        return publicUrl.isEmpty() ? "" : "\n👉 " + label + " : " + publicUrl + path;
    }

    private static String signature() {
        return "\n\n_" + AekdPdf.associationShortName() + " – Tontine_";
    }

    private String announcement(Session session) {
        StringBuilder text = new StringBuilder("📅 *").append(session.getLabel()).append("*\n");
        if (session.getSessionDate() != null) {
            text.append("Date : ").append(DocumentFormat.longDate(session.getSessionDate())).append('\n');
        }
        if (session.getLocation() != null && !session.getLocation().isBlank()) {
            text.append("Lieu : ").append(session.getLocation()).append('\n');
        }
        if (session.getHostMember() != null) {
            text.append("Récepteur : ").append(session.getHostMember().getFullName()).append('\n');
        }
        List<String> beneficiaries = beneficiaryNames(session);
        if (!beneficiaries.isEmpty()) {
            text.append("Bénéficiaire").append(beneficiaries.size() > 1 ? "s" : "").append(" : ")
                    .append(String.join(", ", beneficiaries)).append('\n');
        }
        List<ContributionPeriod> periods = activePeriods(session);
        if (!periods.isEmpty()) {
            text.append("\n*Cotisations attendues*\n");
            for (ContributionPeriod period : periods) {
                ContributionDefinition d = period.getContributionDefinition();
                text.append("• ").append(d.getName()).append(" : ")
                        .append(d.getAmount() != null ? DocumentFormat.fcfa(d.getAmount()) : "montant libre")
                        .append(d.isMandatory() ? "" : " (facultative)");
                if (period.getDueDate() != null) {
                    text.append(" – avant le ").append(DocumentFormat.date(period.getDueDate()));
                }
                text.append('\n');
            }
        }
        return text.toString().stripTrailing();
    }

    private String status(Session session) {
        StringBuilder text = new StringBuilder("📊 *État des cotisations – ").append(session.getLabel()).append("*\n");
        List<ContributionPeriod> periods = activePeriods(session);
        if (periods.isEmpty()) {
            return text.append("Aucune cotisation pour cette séance.").toString();
        }
        for (ContributionPeriod period : periods) {
            ContributionDefinition d = period.getContributionDefinition();
            Map<ContributionPeriodStatus, List<String>> byStatus = new EnumMap<>(ContributionPeriodStatus.class);
            for (ContributionParticipant participant : participantRepository.findForPeriod(period)) {
                Member member = participant.getMember();
                byStatus.computeIfAbsent(statusService.resolve(period, member.getId()), s -> new ArrayList<>())
                        .add(member.getFullName());
            }
            byStatus.values().forEach(names -> names.sort(String.CASE_INSENSITIVE_ORDER));
            text.append("\n*").append(d.getName()).append("*\n");
            line(text, "✅ Payé", byStatus.get(ContributionPeriodStatus.PAID));
            line(text, "⏳ En attente de validation", byStatus.get(ContributionPeriodStatus.PENDING));
            line(text, "🟠 Payé en partie", byStatus.get(ContributionPeriodStatus.PARTIAL));
            line(text, "🔴 En retard", byStatus.get(ContributionPeriodStatus.LATE));
            line(text, "⚪ Pas encore payé", byStatus.get(ContributionPeriodStatus.NOT_PAID));
        }
        return text.toString().stripTrailing();
    }

    private String payout(Session session) {
        PayoutOverview overview = payoutService.overview(session.getId());
        StringBuilder text = new StringBuilder("🎁 *Cagnotte – ").append(session.getLabel()).append("*\n")
                .append("Collecté : ").append(DocumentFormat.fcfa(overview.collected())).append('\n')
                .append("Remis : ").append(DocumentFormat.fcfa(overview.remitted())).append('\n');
        if (!overview.beneficiaries().isEmpty()) {
            text.append('\n');
            for (BeneficiaryShare share : overview.beneficiaries()) {
                text.append("• ").append(share.memberFullName()).append(" : ")
                        .append(DocumentFormat.fcfa(share.remitted())).append(" reçus sur ")
                        .append(DocumentFormat.fcfa(share.share()))
                        .append(share.due().signum() == 0 && share.share().signum() > 0 ? " ✅" : "").append('\n');
            }
        }
        return text.toString().stripTrailing();
    }

    private static void line(StringBuilder text, String label, List<String> names) {
        if (names != null && !names.isEmpty()) {
            text.append(label).append(" (").append(names.size()).append(") : ").append(String.join(", ", names)).append('\n');
        }
    }

    private List<String> beneficiaryNames(Session session) {
        return beneficiaryRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(SessionBeneficiary::getMember)
                .map(Member::getFullName)
                .toList();
    }

    /** Cotisations actives de la séance : obligatoires d'abord, puis par nom. */
    private List<ContributionPeriod> activePeriods(Session session) {
        return periodRepository.findBySessionId(session.getId()).stream()
                .filter(p -> p.getContributionDefinition().getStatus() == ContributionStatus.ACTIVE)
                .sorted(Comparator.comparing((ContributionPeriod p) -> !p.getContributionDefinition().isMandatory())
                        .thenComparing(p -> p.getContributionDefinition().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
