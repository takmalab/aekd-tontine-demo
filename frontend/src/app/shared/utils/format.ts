/** Formate un montant en FCFA, ex. 62000 -> "62 000 FCFA". */
export function formatFcfa(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) {
    return '—';
  }
  return `${new Intl.NumberFormat('fr-FR').format(amount)} FCFA`;
}

/** Convertit une Date locale en "YYYY-MM-DD" (LocalDate côté backend), sans décalage de fuseau. */
export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Parse une LocalDate "YYYY-MM-DD" en Date locale (minuit). */
export function fromIsoDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}
