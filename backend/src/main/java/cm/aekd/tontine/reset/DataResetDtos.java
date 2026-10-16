package cm.aekd.tontine.reset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Échanges de la réinitialisation des données (docs/decisions.md n° 63). */
public final class DataResetDtos {

    private DataResetDtos() {
    }

    /** Un élément et son nombre (ex. « Cotisations », 4). */
    public record Item(String label, long count) {
    }

    /** Une catégorie, ce qu'elle entraîne et ce qu'elle contient aujourd'hui. */
    public record CategoryPreview(DataResetCategory category, String label, List<DataResetCategory> implies,
                                  List<Item> items) {
    }

    /** {@code confirmation} : le mot REINITIALISER ; {@code password} : celui du super administrateur. */
    public record ResetRequest(@NotEmpty List<DataResetCategory> categories, @NotBlank String confirmation,
                               @NotBlank String password) {
    }

    /** Catégories effectivement effacées (dépendances comprises) et ce qui a été supprimé. */
    public record ResetResult(List<DataResetCategory> categories, List<Item> deleted, int deletedFiles) {
    }
}
