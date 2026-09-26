import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { AppPagination } from '../../../shared/components/pagination/pagination';
import { PageState } from '../../../shared/components/page-state/page-state';
import { SearchBar } from '../../../shared/components/search-bar/search-bar';
import { ExportMenu } from '../../../shared/components/export-menu/export-menu';
import { paginate } from '../../../shared/utils/pagination';
import { AUDIT_ACTION_LABELS, AuditAction, AuditLog } from '../audit.model';
import { AuditService } from '../audit.service';

type SortDirection = 'desc' | 'asc';

/** Journal d'audit (CLAUDE.md §26) : consultation réservée à l'ADMIN. */
@Component({
  selector: 'app-audit-log',
  imports: [
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    PageState,
    SearchBar,
    AppPagination,
    ExportMenu,
  ],
  templateUrl: './audit-log.html',
  styleUrl: './audit-log.scss',
})
export class AuditLogPage implements OnInit {
  private readonly auditService = inject(AuditService);

  readonly actionLabels = AUDIT_ACTION_LABELS;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly logs = signal<AuditLog[]>([]);

  readonly search = signal('');
  readonly actionFilter = signal<AuditAction | 'ALL'>('ALL');
  readonly direction = signal<SortDirection>('desc');
  readonly pageIndex = signal<number>(0);
  readonly pageSize = signal<number>(25);

  /** Actions effectivement présentes dans le journal (pas les 17 valeurs possibles). */
  readonly availableActions = computed(() => {
    const seen = new Set(this.logs().map((l) => l.action));
    return [...seen].sort((a, b) => this.actionLabels[a].localeCompare(this.actionLabels[b], 'fr'));
  });

  readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const action = this.actionFilter();
    const dir = this.direction();

    let list = this.logs();
    if (action !== 'ALL') {
      list = list.filter((l) => l.action === action);
    }
    if (term) {
      list = list.filter((log) =>
        [log.userEmail ?? '', this.actionLabels[log.action], log.entityType, log.description]
          .join(' ')
          .toLowerCase()
          .includes(term),
      );
    }

    const sorted = [...list].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt));
    return dir === 'desc' ? sorted.reverse() : sorted;
  });

  readonly paged = computed(() => paginate(this.filtered(), this.pageIndex(), this.pageSize()));

  readonly exportRows = computed(() =>
    this.filtered().map((log) => ({
      Date: log.occurredAt,
      Utilisateur: log.userEmail,
      Action: this.actionLabels[log.action],
      Entité: log.entityType,
      'ID entité': log.entityId,
      Description: log.description,
    })),
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.auditService.list().subscribe({
      next: (logs) => {
        this.logs.set(logs);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  onSearch(term: string): void {
    this.search.set(term);
    this.pageIndex.set(0);
  }

  onActionFilter(action: AuditAction | 'ALL'): void {
    this.actionFilter.set(action);
    this.pageIndex.set(0);
  }

  toggleDirection(): void {
    this.direction.update((d) => (d === 'desc' ? 'asc' : 'desc'));
  }
}
