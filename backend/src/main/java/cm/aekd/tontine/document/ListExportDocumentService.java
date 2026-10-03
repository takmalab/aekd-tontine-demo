package cm.aekd.tontine.document;

import cm.aekd.tontine.document.AekdPdf.Cell;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Export PDF générique d'une liste (membres, paiements, prêts, sanctions,
 * journal d'audit…) à partir des lignes affichées à l'écran. Le serveur ne fait
 * que la mise en page : les données ont déjà été obtenues par l'utilisateur via
 * l'API, avec ses droits. Réservé à ADMIN et TRESORIER (écrans concernés).
 */
@Service
public class ListExportDocumentService {

    /** Au-delà, le tableau passe en paysage pour rester lisible. */
    private static final int PORTRAIT_MAX_COLUMNS = 6;

    private final DocumentAccess access;

    public ListExportDocumentService(DocumentAccess access) {
        this.access = access;
    }

    public GeneratedDocument export(ListExportRequest request) {
        List<String> columns = request.columns();
        List<List<String>> rows = request.rows() == null ? List.of() : request.rows();
        String title = request.title().trim();

        AekdPdf pdf = columns.size() > PORTRAIT_MAX_COLUMNS
                ? AekdPdf.landscape(title, access.currentAuthorName())
                : AekdPdf.portrait(title, access.currentAuthorName());
        String filters = request.filters() == null || request.filters().isBlank() ? null : clean(request.filters().trim());
        pdf.heading(title, rows.size() + " élément" + (rows.size() > 1 ? "s" : "")
                + (filters != null ? " – " + filters : ""));

        List<Cell[]> cells = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            Cell[] line = new Cell[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                line[i] = Cell.of(i < row.size() ? clean(row.get(i)) : null);
            }
            cells.add(line);
        }
        float[] widths = new float[columns.size()];
        Arrays.fill(widths, 1f);
        int[] right = request.rightAlign() == null ? new int[0]
                : request.rightAlign().stream().filter(i -> i != null && i >= 0 && i < columns.size())
                .mapToInt(Integer::intValue).toArray();
        pdf.table(columns.stream().map(ListExportDocumentService::clean).toArray(String[]::new), widths, cells, right);
        pdf.note("Export reflétant la recherche et les filtres actifs à l'écran au moment de l'export.");

        return new GeneratedDocument(DocumentFormat.slug(title) + ".pdf", pdf.finish());
    }

    /**
     * Les montants formatés par le navigateur utilisent l'espace fine insécable
     * (U+202F), absente de la police PDF standard (WinAnsi) : remplacée par
     * l'espace insécable classique pour ne pas disparaître du document.
     */
    private static String clean(String text) {
        return text == null ? null : text.replace('\u202F', '\u00A0').replace('\u2009', ' ');
    }
}
