import { Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { exportToCsv } from '../../utils/csv-export';

/**
 * Bouton d'export CSV. Reçoit les lignes déjà filtrées/affichées par l'écran
 * appelant (jamais un ré-appel API séparé) : l'export reflète toujours
 * exactement la recherche/les filtres actifs à l'écran, conformément à la
 * décision validée (docs/decisions.md #29).
 */
@Component({
  selector: 'app-export-menu',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <button mat-stroked-button type="button" [disabled]="rows().length === 0" (click)="export()">
      <mat-icon>download</mat-icon>
      Exporter CSV
    </button>
  `,
})
export class ExportMenu {
  readonly filename = input.required<string>();
  readonly rows = input.required<Record<string, string | number | null>[]>();

  export(): void {
    exportToCsv(this.filename(), this.rows());
  }
}
