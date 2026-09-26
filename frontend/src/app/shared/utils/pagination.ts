/** Tranche un tableau déjà chargé pour une pagination 100% côté client (pageIndex commence à 0). */
export function paginate<T>(items: readonly T[], pageIndex: number, pageSize: number): T[] {
  const start = pageIndex * pageSize;
  return items.slice(start, start + pageSize);
}

export function pageCount(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / pageSize));
}
