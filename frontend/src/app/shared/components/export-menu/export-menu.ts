import { Component, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { DocumentService, ListExportRequest } from '../../../core/services/document.service';
import { exportToCsv } from '../../utils/csv-export';

type ExportValue = string | number | null;

/**
 * Menu d'export PDF, Excel ou CSV. Reçoit les lignes déjà filtrées/affichées par
 * l'écran appelant (jamais un ré-appel API séparé) : l'export reflète toujours
 * exactement la recherche/les filtres actifs à l'écran (docs/decisions.md #29).
 * Le PDF est mis en page par le backend aux couleurs de l'AEKD (#37).
 */
@Component({
  selector: 'app-export-menu',
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <button mat-stroked-button type="button" [disabled]="rows().length === 0" [matMenuTriggerFor]="menu">
      <mat-icon>download</mat-icon>
      Exporter
    </button>
    <mat-menu #menu="matMenu">
      <button mat-menu-item type="button" (click)="exportPdf()">
        <mat-icon>picture_as_pdf</mat-icon>
        <span>PDF (mis en page)</span>
      </button>
      <button mat-menu-item type="button" (click)="exportExcel()">
        <mat-icon>grid_on</mat-icon>
        <span>Excel (.xlsx)</span>
      </button>
      <button mat-menu-item type="button" (click)="exportCsv()">
        <mat-icon>table_view</mat-icon>
        <span>CSV (tableur)</span>
      </button>
    </mat-menu>
  `,
})
export class ExportMenu {
  private readonly documentService = inject(DocumentService);

  readonly filename = input.required<string>();
  readonly rows = input.required<Record<string, ExportValue>[]>();
  /** Titre du PDF (ex. « Journal d'audit »). */
  readonly title = input.required<string>();
  /** Recherche / filtres actifs, rappelés sous le titre du PDF. */
  readonly filters = input<string | null>(null);

  exportCsv(): void {
    exportToCsv(this.filename(), this.rows());
  }

  exportPdf(): void {
    const request = this.request();
    if (request) {
      this.documentService.exportList(request);
    }
  }

  /** Mêmes lignes que le PDF ; le serveur convertit les montants en nombres (decisions.md n° 62). */
  exportExcel(): void {
    const request = this.request();
    if (request) {
      this.documentService.exportListExcel(request);
    }
  }

  private request(): ListExportRequest | null {
    const rows = this.rows();
    if (rows.length === 0) {
      return null;
    }
    const columns = Object.keys(rows[0]);
    const cells = rows.map((row) => columns.map((col) => toPdfText(row[col])));
    // Colonnes de montants ou de nombres (cases vides ignorées) : alignées à droite.
    const rightAlign = columns
      .map((col, i) => {
        const filled = rows.map((row) => row[col]).filter((v) => v !== null && v !== undefined && v !== '');
        return { i, numeric: filled.length > 0 && filled.every(isNumeric) };
      })
      .filter((c) => c.numeric)
      .map((c) => c.i);
    return { title: this.title(), filters: this.filters(), columns, rows: cells, rightAlign };
  }
}

function isNumeric(value: ExportValue): boolean {
  return typeof value === 'number' || (typeof value === 'string' && /^-?[\d\s\u00a0\u202f]+(FCFA)?$/.test(value.trim()));
}

/** Dates ISO rendues lisibles (« 03/10/2026 14:05 ») ; le reste tel quel. */
function toPdfText(value: ExportValue): string {
  if (value === null || value === undefined) {
    return '';
  }
  const text = String(value);
  const dateTime = text.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  if (dateTime) {
    const d = new Date(text);
    if (!Number.isNaN(d.getTime())) {
      const pad = (n: number) => String(n).padStart(2, '0');
      return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    }
  }
  const date = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  return date ? `${date[3]}/${date[2]}/${date[1]}` : text;
}
