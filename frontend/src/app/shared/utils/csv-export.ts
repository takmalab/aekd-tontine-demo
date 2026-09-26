/**
 * Export CSV côté frontend, sans dépendance (Blob natif). S'ouvre nativement
 * dans Excel/LibreOffice. Décision validée par le propriétaire du projet le
 * 2026-09-26 (voir docs/decisions.md #29) : hors périmètre MVP initial, mais
 * explicitement demandé désormais ; pas d'export .xlsx/.pdf réel pour l'instant
 * (aucune dépendance supplémentaire tant que non redemandé).
 *
 * Le tableau `rows` doit déjà être filtré/trié par l'appelant : l'export
 * reflète toujours exactement ce qui est affiché à l'écran, jamais un
 * ré-appel API séparé.
 */
/** Partie pure (testable sans DOM) : construit le contenu CSV. */
export function toCsvString(rows: Record<string, string | number | null>[]): string {
  if (rows.length === 0) {
    return '';
  }

  const columns = Object.keys(rows[0]);
  const escape = (value: string | number | null): string => {
    const text = value === null || value === undefined ? '' : String(value);
    return /[",;\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  };

  const lines = [columns.join(';'), ...rows.map((row) => columns.map((col) => escape(row[col])).join(';'))];
  return lines.join('\r\n');
}

export function exportToCsv(filename: string, rows: Record<string, string | number | null>[]): void {
  const content = toCsvString(rows);
  if (!content) {
    return;
  }

  // BOM UTF-8 : Excel détecte correctement les accents sans lui.
  const blob = new Blob(['﻿' + content], { type: 'text/csv;charset=utf-8;' });

  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename.endsWith('.csv') ? filename : `${filename}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
