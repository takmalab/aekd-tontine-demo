package cm.aekd.tontine.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Export PDF d'une liste telle qu'affichée à l'écran (docs/decisions.md n° 37) :
 * le frontend envoie les lignes déjà filtrées, comme pour l'export CSV (n° 29).
 *
 * @param filters    description des filtres/recherche actifs, affichée sous le titre (facultatif)
 * @param rightAlign index des colonnes à aligner à droite (montants), facultatif
 */
public record ListExportRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 300) String filters,
        @NotEmpty @Size(max = 15) List<@Size(max = 80) String> columns,
        @Size(max = 5000) List<@Size(max = 15) List<@Size(max = 500) String>> rows,
        @Size(max = 15) List<Integer> rightAlign
) {
}
