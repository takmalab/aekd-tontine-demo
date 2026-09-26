import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { formatFcfa } from '../../../shared/utils/format';
import { APPLIED_STATUS_LABELS, AppliedSanction, SANCTION_TYPE_LABELS } from '../sanction.model';
import { SanctionService } from '../sanction.service';

/** « Mes sanctions » (CLAUDE.md §6 MEMBRE) : sanctions réellement appliquées au membre connecté. */
@Component({
  selector: 'app-my-sanctions',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './my-sanctions.html',
  styleUrl: './my-sanctions.scss',
})
export class MySanctions implements OnInit {
  private readonly sanctionService = inject(SanctionService);

  readonly typeLabels = SANCTION_TYPE_LABELS;
  readonly statusLabels = APPLIED_STATUS_LABELS;
  readonly formatFcfa = formatFcfa;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly noMemberProfile = signal(false);
  readonly sanctions = signal<AppliedSanction[]>([]);

  readonly active = computed(() => this.sanctions().filter((s) => s.status === 'APPLIED'));
  readonly activeAmount = computed(() => this.active().reduce((sum, s) => sum + (s.amount ?? 0), 0));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.sanctionService.mine().subscribe({
      next: (list) => {
        this.sanctions.set(
          [...list].sort(
            (a, b) => Number(a.status === 'CANCELLED') - Number(b.status === 'CANCELLED') || b.appliedAt.localeCompare(a.appliedAt),
          ),
        );
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.noMemberProfile.set(err.status === 403);
        this.error.set(err.status !== 403);
        this.loading.set(false);
      },
    });
  }
}
