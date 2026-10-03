package cm.aekd.tontine.document;

import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionService;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionPeriodStatus;
import cm.aekd.tontine.contribution.ParticipantPaymentStatusResponse;
import cm.aekd.tontine.document.AekdPdf.Cell;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionBeneficiary;
import cm.aekd.tontine.session.SessionBeneficiaryRepository;
import cm.aekd.tontine.session.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static cm.aekd.tontine.document.DocumentFormat.date;
import static cm.aekd.tontine.document.DocumentFormat.fcfa;

/**
 * Rapport de séance : pour chaque cotisation rattachée à la séance, la
 * situation de chaque participant (même calcul que l'écran de détail d'une
 * cotisation), puis un récapitulatif. Chaque cotisation reste distincte
 * (CLAUDE.md §9 : pas de fusion). Réservé à ADMIN et TRESORIER.
 */
@Service
@Transactional(readOnly = true)
public class SessionReportDocumentService {

    private final SessionRepository sessionRepository;
    private final SessionBeneficiaryRepository beneficiaryRepository;
    private final ContributionPeriodRepository periodRepository;
    private final ContributionDefinitionService definitionService;
    private final DocumentAccess access;

    public SessionReportDocumentService(SessionRepository sessionRepository,
                                        SessionBeneficiaryRepository beneficiaryRepository,
                                        ContributionPeriodRepository periodRepository,
                                        ContributionDefinitionService definitionService,
                                        DocumentAccess access) {
        this.sessionRepository = sessionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.periodRepository = periodRepository;
        this.definitionService = definitionService;
        this.access = access;
    }

    public GeneratedDocument report(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        List<SessionBeneficiary> beneficiaries = beneficiaryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        // Obligatoires d'abord, puis montant décroissant, puis nom (même ordre que l'écran de la séance).
        List<ContributionPeriod> periods = periodRepository.findBySessionId(sessionId).stream()
                .sorted(Comparator
                        .comparing((ContributionPeriod p) -> !p.getContributionDefinition().isMandatory())
                        .thenComparing(p -> p.getContributionDefinition().getAmount(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(p -> p.getContributionDefinition().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        AekdPdf pdf = AekdPdf.portrait("Rapport de séance – " + session.getLabel(), access.currentAuthorName());
        pdf.heading("Rapport de séance", session.getLabel() + " – " + DocumentFormat.longDate(session.getSessionDate()));

        pdf.keyValues(List.of(
                new String[]{"Date", date(session.getSessionDate())},
                new String[]{"Lieu", session.getLocation()},
                new String[]{"Récepteur", session.getHostMember() != null ? session.getHostMember().getFullName() : null},
                new String[]{"Bénéficiaire(s)", beneficiaries.isEmpty() ? null : beneficiaries.stream()
                        .map(b -> b.getMember().getFullName()).collect(Collectors.joining(", "))}
        ));

        if (periods.isEmpty()) {
            pdf.note("Aucune cotisation n'est rattachée à cette séance.");
            return new GeneratedDocument(filename(session), pdf.finish());
        }

        List<Cell[]> recap = new ArrayList<>();
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalExpected = BigDecimal.ZERO;

        for (ContributionPeriod period : periods) {
            ContributionDefinition definition = period.getContributionDefinition();
            List<ParticipantPaymentStatusResponse> statuses =
                    definitionService.listParticipantStatuses(definition.getId(), period.getId());
            boolean fixed = definition.getAmountMode() == AmountMode.FIXED;

            pdf.section(definition.getName() + " – "
                    + (fixed ? fcfa(definition.getAmount()) : "montant libre")
                    + " – " + (definition.isMandatory() ? "obligatoire" : "facultative"));
            pdf.paragraph("Destination : " + DocumentFormat.destination(definition.getFundDestination())
                    + "   ·   Échéance : " + date(period.getDueDate())
                    + "   ·   Participants : " + statuses.size());

            List<Cell[]> rows = new ArrayList<>();
            BigDecimal collected = BigDecimal.ZERO;
            BigDecimal pending = BigDecimal.ZERO;
            BigDecimal remaining = BigDecimal.ZERO;
            int paid = 0;
            int late = 0;
            for (ParticipantPaymentStatusResponse s : statuses) {
                rows.add(new Cell[]{
                        Cell.of(s.memberFullName()),
                        Cell.colored(DocumentFormat.participantStatus(s.status(), definition.isMandatory()),
                                statusColor(s.status(), definition.isMandatory())),
                        Cell.of(fixed ? fcfa(s.dueAmount()) : "Libre"),
                        Cell.of(fcfa(s.validatedAmount())),
                        Cell.of(fcfa(s.pendingAmount())),
                        Cell.of(fixed ? fcfa(s.remainingAmount()) : "–")
                });
                collected = collected.add(nz(s.validatedAmount()));
                pending = pending.add(nz(s.pendingAmount()));
                remaining = remaining.add(nz(s.remainingAmount()));
                if (s.status() == ContributionPeriodStatus.PAID) {
                    paid++;
                }
                if (s.status() == ContributionPeriodStatus.LATE && definition.isMandatory()) {
                    late++;
                }
            }
            BigDecimal expected = fixed ? nz(definition.getAmount()).multiply(BigDecimal.valueOf(statuses.size())) : null;
            rows.add(new Cell[]{
                    Cell.total("Total"), Cell.total(paid + " payé(s)"),
                    Cell.total(expected != null ? fcfa(expected) : ""), Cell.total(fcfa(collected)),
                    Cell.total(fcfa(pending)), Cell.total(fixed ? fcfa(remaining) : "")
            });
            pdf.table(new String[]{"Membre", "Statut", "Dû", "Validé", "En attente", "Reste"},
                    new float[]{3.2f, 1.6f, 1.4f, 1.4f, 1.4f, 1.4f}, rows, 2, 3, 4, 5);

            recap.add(new Cell[]{
                    Cell.strong(definition.getName()),
                    Cell.of(definition.isMandatory() ? "Obligatoire" : "Facultative"),
                    Cell.of(String.valueOf(statuses.size())),
                    Cell.of(String.valueOf(paid)),
                    Cell.of(definition.isMandatory() ? String.valueOf(late) : "–"),
                    Cell.of(fcfa(collected)),
                    Cell.of(expected != null ? fcfa(expected) : "Libre")
            });
            totalCollected = totalCollected.add(collected);
            if (expected != null && definition.isMandatory()) {
                totalExpected = totalExpected.add(expected);
            }
        }

        pdf.section("Récapitulatif de la séance");
        recap.add(new Cell[]{
                Cell.total("Total"), Cell.total(""), Cell.total(""), Cell.total(""), Cell.total(""),
                Cell.total(fcfa(totalCollected)), Cell.total(fcfa(totalExpected) + " (oblig.)")
        });
        pdf.table(new String[]{"Cotisation", "Caractère", "Participants", "Payés", "En retard", "Collecté (validé)", "Attendu"},
                new float[]{3f, 1.5f, 1.3f, 1f, 1.1f, 1.7f, 1.8f}, recap, 2, 3, 4, 5, 6);
        pdf.note("Montants « validés » : seuls les paiements validés par la trésorerie sont comptés comme encaissés. "
                + "Le total attendu ne porte que sur les cotisations obligatoires à montant fixe.");

        return new GeneratedDocument(filename(session), pdf.finish());
    }

    private static String filename(Session session) {
        return "rapport-seance-" + DocumentFormat.slug(session.getLabel()) + "-" + session.getSessionDate() + ".pdf";
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Color statusColor(ContributionPeriodStatus status, boolean mandatory) {
        return switch (status) {
            case PAID -> AekdPdf.GREEN;
            case PENDING -> AekdPdf.ORANGE;
            case PARTIAL -> AekdPdf.VIOLET;
            case LATE -> mandatory ? AekdPdf.RED : AekdPdf.MUTED;
            case NOT_PAID -> AekdPdf.MUTED;
        };
    }
}
