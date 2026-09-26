import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { pageCount } from '../../utils/pagination';

export const DEFAULT_PAGE_SIZES = [10, 25, 50] as const;

/**
 * Pagination 100% côté client (le tableau est déjà chargé en mémoire — le
 * backend ne fournit pas de `Pageable`/`Page<>` aujourd'hui, cf. plan). Le
 * composant appelant fournit `total` et gère l'index de page.
 */
@Component({
  selector: 'app-pagination',
  imports: [MatButtonModule, MatIconModule, MatSelectModule],
  template: `
    @if (total() > 0) {
      <div class="pagination">
        <div class="pagination__size">
          <span>Par page</span>
          <mat-select [value]="pageSize()" (selectionChange)="pageSizeChange.emit($event.value)">
            @for (size of pageSizes(); track size) {
              <mat-option [value]="size">{{ size }}</mat-option>
            }
          </mat-select>
        </div>

        <span class="pagination__summary">{{ rangeLabel() }} sur {{ total() }}</span>

        <div class="pagination__nav">
          <button mat-icon-button type="button" [disabled]="pageIndex() === 0" (click)="pageIndexChange.emit(0)" aria-label="Première page">
            <mat-icon>first_page</mat-icon>
          </button>
          <button mat-icon-button type="button" [disabled]="pageIndex() === 0" (click)="pageIndexChange.emit(pageIndex() - 1)" aria-label="Page précédente">
            <mat-icon>chevron_left</mat-icon>
          </button>
          <span class="pagination__page">Page {{ pageIndex() + 1 }} / {{ lastIndex() + 1 }}</span>
          <button mat-icon-button type="button" [disabled]="pageIndex() >= lastIndex()" (click)="pageIndexChange.emit(pageIndex() + 1)" aria-label="Page suivante">
            <mat-icon>chevron_right</mat-icon>
          </button>
          <button mat-icon-button type="button" [disabled]="pageIndex() >= lastIndex()" (click)="pageIndexChange.emit(lastIndex())" aria-label="Dernière page">
            <mat-icon>last_page</mat-icon>
          </button>
        </div>
      </div>
    }
  `,
  styles: `
    .pagination {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 10px 16px;
      margin: 4px 0 20px;
      padding: 6px 4px;
      font-size: 13px;
      color: var(--aekd-gray-muted);

      &__size {
        display: flex;
        align-items: center;
        gap: 8px;

        mat-select {
          width: 56px;
        }
      }

      &__nav {
        display: flex;
        align-items: center;
        gap: 2px;
      }

      &__page {
        margin: 0 6px;
        font-weight: 600;
        color: var(--aekd-gray);
        white-space: nowrap;
      }
    }
  `,
})
export class AppPagination {
  readonly total = input.required<number>();
  readonly pageIndex = input<number>(0);
  readonly pageSize = input<number>(DEFAULT_PAGE_SIZES[1]);
  readonly pageSizes = input<readonly number[]>(DEFAULT_PAGE_SIZES);

  readonly pageIndexChange = output<number>();
  readonly pageSizeChange = output<number>();

  readonly lastIndex = computed(() => pageCount(this.total(), this.pageSize()) - 1);

  readonly rangeLabel = computed(() => {
    const start = this.pageIndex() * this.pageSize() + 1;
    const end = Math.min(this.total(), start + this.pageSize() - 1);
    return `${start}–${end}`;
  });
}
