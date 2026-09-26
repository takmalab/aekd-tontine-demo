import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { formatFcfa } from '../../../shared/utils/format';
import { ContributionDefinition } from '../../contributions/contribution.model';
import { ContributionService } from '../../contributions/contribution.service';
import { RuleFormDialog, RuleFormData } from '../rule-form-dialog/rule-form-dialog';
import {
  APPLIED_STATUS_LABELS,
  AppliedSanction,
  AppliedSanctionStatus,
  SANCTION_TYPE_LABELS,
  SanctionCandidate,
  SanctionRule,
} from '../sanction.model';
import { SanctionService } from '../sanction.service';

type AppliedFilter = 'ALL' | AppliedSanctionStatus;

interface CandidatesState {
  loading: boolean;
  error: boolean;
  list: SanctionCandidate[];
}

/**
 * Sanctions, vue ADMIN et TRESORIER (CLAUDE.md §18-§19), par cotisation :
 * règles (création ADMIN + TRESORIER, cotisation obligatoire uniquement),
 * candidats et application / annulation (TRESORIER uniquement), sanctions appliquées.
 */
@Component({
  selector: 'app-staff-sanctions',
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  templateUrl: './staff-sanctions.html',
  styleUrl: './staff-sanctions.scss',
})
export class StaffSanctions implements OnInit {
  private readonly contributionService = inject(ContributionService);
  private readonly sanctionService = inject(SanctionService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  /** Paramètre de requête `?contribution=<id>` (lien depuis le détail d'une cotisation). */
  readonly contribution = input<string | undefined>();

  /** Candidats, application et annulation : TRESORIER uniquement (pas l'ADMIN). */
  readonly isTreasurer = this.authService.hasRole('TRESORIER');
  readonly typeLabels = SANCTION_TYPE_LABELS;
  readonly statusLabels = APPLIED_STATUS_LABELS;
  readonly formatFcfa = formatFcfa;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly contributions = signal<ContributionDefinition[]>([]);
  readonly selectedId = signal<string | null>(null);

  readonly detailLoading = signal(false);
  readonly detailError = signal(false);
  readonly rules = signal<SanctionRule[]>([]);
  readonly applied = signal<AppliedSanction[]>([]);
  readonly candidates = signal<Record<string, CandidatesState>>({});
  readonly appliedFilter = signal<AppliedFilter>('APPLIED');
  readonly busy = signal<string | null>(null);

  readonly mandatory = computed(() => this.contributions().filter((c) => c.mandatory));
  readonly optional = computed(() => this.contributions().filter((c) => !c.mandatory));
  readonly selected = computed(() => this.contributions().find((c) => c.id === this.selectedId()) ?? null);

  readonly appliedCounts = computed(() => {
    const list = this.applied();
    return {
      ALL: list.length,
      APPLIED: list.filter((s) => s.status === 'APPLIED').length,
      CANCELLED: list.filter((s) => s.status === 'CANCELLED').length,
    } as Record<AppliedFilter, number>;
  });

  readonly visibleApplied = computed(() => {
    const f = this.appliedFilter();
    return f === 'ALL' ? this.applied() : this.applied().filter((s) => s.status === f);
  });

  readonly appliedFilters: { value: AppliedFilter; label: string }[] = [
    { value: 'APPLIED', label: 'Appliquées' },
    { value: 'CANCELLED', label: 'Annulées' },
    { value: 'ALL', label: 'Toutes' },
  ];

  ngOnInit(): void {
    this.contributionService.list().subscribe({
      next: (list) => {
        // Obligatoires actives d'abord (là où les sanctions ont un sens), puis montant décroissant.
        const sorted = [...list].sort(
          (a, b) =>
            Number(b.mandatory) - Number(a.mandatory) ||
            Number(b.status === 'ACTIVE') - Number(a.status === 'ACTIVE') ||
            (b.amount ?? -1) - (a.amount ?? -1) ||
            a.name.localeCompare(b.name, 'fr'),
        );
        this.contributions.set(sorted);
        this.loading.set(false);
        const requested = this.contribution();
        const initial = sorted.find((c) => c.id === requested) ?? sorted.find((c) => c.mandatory) ?? sorted[0];
        if (initial) {
          this.select(initial.id);
        }
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  select(id: string): void {
    this.selectedId.set(id);
    this.candidates.set({});
    // Garde la cotisation dans l'URL (lien partageable, retour arrière).
    this.router.navigate([], { queryParams: { contribution: id }, replaceUrl: true });
    this.loadDetail();
  }

  loadDetail(): void {
    const id = this.selectedId();
    if (!id) {
      return;
    }
    this.detailLoading.set(true);
    this.detailError.set(false);
    forkJoin({
      rules: this.sanctionService.listRules(id),
      applied: this.sanctionService.applied(id),
    }).subscribe({
      next: ({ rules, applied }) => {
        this.rules.set(rules);
        this.applied.set([...applied].sort((a, b) => b.appliedAt.localeCompare(a.appliedAt)));
        this.detailLoading.set(false);
      },
      error: () => {
        this.detailError.set(true);
        this.detailLoading.set(false);
      },
    });
  }

  createRule(): void {
    const contribution = this.selected();
    if (!contribution || !contribution.mandatory) {
      return;
    }
    const data: RuleFormData = { contribution };
    this.dialog
      .open(RuleFormDialog, { data, width: '540px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((rule?: SanctionRule) => {
        if (rule) {
          this.rules.update((list) => [...list, rule]);
          this.snackBar.open('Règle de sanction créée', 'OK', { duration: 4000 });
        }
      });
  }

  ruleSummary(rule: SanctionRule): string {
    const what = rule.type === 'MONETARY' ? formatFcfa(rule.monetaryAmount) : (rule.description ?? '');
    return rule.type === 'MONETARY' ? what : `« ${what} »`;
  }

  thresholdLabel(days: number): string {
    return days === 0 ? "Dès le lendemain de l'échéance" : `À partir de ${days} jour${days > 1 ? 's' : ''} après l'échéance`;
  }

  // ---------- Candidats (TRESORIER) ----------
  toggleCandidates(rule: SanctionRule): void {
    const current = this.candidates()[rule.id];
    if (current && !current.loading) {
      const { [rule.id]: _, ...rest } = this.candidates();
      this.candidates.set(rest);
      return;
    }
    this.loadCandidates(rule);
  }

  loadCandidates(rule: SanctionRule): void {
    this.candidates.update((c) => ({ ...c, [rule.id]: { loading: true, error: false, list: [] } }));
    this.sanctionService.candidates(rule.id).subscribe({
      next: (list) =>
        this.candidates.update((c) => ({
          ...c,
          [rule.id]: { loading: false, error: false, list: [...list].sort((a, b) => a.memberFullName.localeCompare(b.memberFullName, 'fr')) },
        })),
      error: () => this.candidates.update((c) => ({ ...c, [rule.id]: { loading: false, error: true, list: [] } })),
    });
  }

  applyTo(rule: SanctionRule, candidate: SanctionCandidate): void {
    const data: ConfirmDialogData = {
      title: 'Appliquer la sanction ?',
      message: `${candidate.memberFullName} — ${this.typeLabels[rule.type].toLowerCase()} : ${this.ruleSummary(rule)}, pour la séance « ${candidate.sessionLabel} ».`,
      confirmLabel: 'Appliquer',
      icon: 'gavel',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '460px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        const key = `${rule.id}:${candidate.memberId}:${candidate.contributionPeriodId}`;
        this.busy.set(key);
        this.sanctionService.apply(rule.id, candidate.memberId, candidate.contributionPeriodId).subscribe({
          next: (sanction) => {
            this.busy.set(null);
            this.applied.update((list) => [sanction, ...list]);
            this.candidates.update((c) => {
              const state = c[rule.id];
              return state
                ? {
                    ...c,
                    [rule.id]: {
                      ...state,
                      list: state.list.filter(
                        (x) => !(x.memberId === candidate.memberId && x.contributionPeriodId === candidate.contributionPeriodId),
                      ),
                    },
                  }
                : c;
            });
            this.snackBar.open(`Sanction appliquée à ${candidate.memberFullName}`, 'OK', { duration: 4000 });
          },
          error: (err: HttpErrorResponse) => {
            this.busy.set(null);
            this.snackBar.open(
              err.status === 409
                ? 'Cette sanction est déjà appliquée pour ce membre et cette séance.'
                : err.status === 400
                  ? "Le membre n'est plus éligible (paiement régularisé ou règle désactivée)."
                  : "L'application a échoué.",
              'OK',
              { duration: 6000 },
            );
            this.loadCandidates(rule);
          },
        });
      });
  }

  // ---------- Sanctions appliquées ----------
  cancel(sanction: AppliedSanction): void {
    const data: ConfirmDialogData = {
      title: 'Annuler cette sanction ?',
      message: `La sanction de ${sanction.memberFullName} (${sanction.sessionLabel}) sera annulée. Elle restera visible dans l'historique avec le statut « Annulée ».`,
      confirmLabel: 'Annuler la sanction',
      cancelLabel: 'Retour',
      icon: 'undo',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '460px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busy.set(sanction.id);
        this.sanctionService.cancel(sanction.id).subscribe({
          next: (updated) => {
            this.busy.set(null);
            this.applied.update((list) => list.map((s) => (s.id === updated.id ? updated : s)));
            this.snackBar.open('Sanction annulée', 'OK', { duration: 4000 });
          },
          error: () => {
            this.busy.set(null);
            this.snackBar.open("L'annulation a échoué (sanction déjà annulée ?).", 'OK', { duration: 6000 });
            this.loadDetail();
          },
        });
      });
  }
}
