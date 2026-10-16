package cm.aekd.tontine.document;

import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.document.AekdPdf.Cell;
import cm.aekd.tontine.tournament.Tournament;
import cm.aekd.tontine.tournament.TournamentRepository;
import cm.aekd.tontine.tournament.TournamentSlot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Ordre de passage d'un tournoi en PDF (docs/decisions.md n° 59), à imprimer ou partager. */
@Service
@Transactional(readOnly = true)
public class TournamentDocumentService {

    private final TournamentRepository tournamentRepository;
    private final DocumentAccess access;

    public TournamentDocumentService(TournamentRepository tournamentRepository, DocumentAccess access) {
        this.tournamentRepository = tournamentRepository;
        this.access = access;
    }

    public GeneratedDocument order(UUID tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournoi introuvable"));

        AekdPdf pdf = AekdPdf.portrait("Ordre de passage – " + tournament.getName(), access.currentAuthorName())
                .background(PdfPattern.SYMBOLES, AekdPdf.ORANGE);
        pdf.heading("Ordre de passage", tournament.getName());
        pdf.keyValues(List.of(
                new String[]{"Séances", tournament.ruleLabel()},
                new String[]{"Membres", String.valueOf(tournament.getSlots().size())},
                new String[]{"Cotisations", tournament.getContributions().stream()
                        .map(ContributionDefinition::getName)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.joining(", "))}
        ));

        pdf.section("Ordre de passage");
        LocalDate today = LocalDate.now(DocumentFormat.ZONE);
        List<Cell[]> rows = new ArrayList<>();
        for (TournamentSlot slot : tournament.getSlots().stream()
                .sorted(Comparator.comparingInt(TournamentSlot::getPosition)).toList()) {
            LocalDate date = tournament.plannedDate(slot);
            String state = slot.getSession() != null ? "Séance créée"
                    : slot.isGenerated() ? "Séance supprimée"
                    : date != null && date.isBefore(today) ? "Passée" : "À venir";
            rows.add(new Cell[]{
                    Cell.strong(String.valueOf(slot.getPosition() + 1)),
                    Cell.strong(slot.getMember().getFullName()),
                    Cell.of(date != null ? DocumentFormat.longDate(date) : null),
                    Cell.of(state)
            });
        }
        pdf.table(new String[]{"N°", "Membre (récepteur)", "Date prévue", "État"},
                new float[]{0.6f, 3f, 2.6f, 1.6f}, rows);
        pdf.note("Chaque séance est créée automatiquement quelques jours avant sa date, chez le membre de son rang.");
        return new GeneratedDocument("ordre-de-passage-" + DocumentFormat.slug(tournament.getName()) + ".pdf", pdf.finish());
    }
}
