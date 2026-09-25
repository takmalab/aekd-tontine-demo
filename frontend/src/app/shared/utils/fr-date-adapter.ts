import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

/**
 * NativeDateAdapter lit une date saisie au clavier au format américain
 * (MM/JJ/AAAA) via Date.parse. Cet adaptateur lit le format français
 * JJ/MM/AAAA (séparateurs / . ou -), et rejette les dates impossibles
 * (ex. 31/02/2026). L'affichage reste géré par NativeDateAdapter (locale fr-FR).
 */
@Injectable()
export class FrDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (trimmed === '') {
        return null;
      }
      const match = /^(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4})$/.exec(trimmed);
      if (!match) {
        return this.invalid();
      }
      const day = Number(match[1]);
      const month = Number(match[2]) - 1;
      const year = Number(match[3]);
      const date = new Date(year, month, day);
      const isReal = date.getFullYear() === year && date.getMonth() === month && date.getDate() === day;
      return isReal ? date : this.invalid();
    }
    return super.parse(value, parseFormat);
  }
}
