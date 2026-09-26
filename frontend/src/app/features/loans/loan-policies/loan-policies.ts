import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { formatFcfa } from '../../../shared/utils/format';
import { LoanPolicy } from '../loan.model';
import { LoanService } from '../loan.service';
import { PolicyFormDialog } from '../policy-form-dialog/policy-form-dialog';

interface Term {
  icon: string;
  label: string;
  value: string;
}

/**
 * Politiques de prêt (CLAUDE.md §20) : consultables par tous ; création et
 * désactivation réservées à l'ADMIN (§6 — pas au trésorier).
 */
@Component({
  selector: 'app-loan-policies',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './loan-policies.html',
  styleUrl: './loan-policies.scss',
})
export class LoanPolicies implements OnInit {
  private readonly loanService = inject(LoanService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly isAdmin = this.authService.hasRole('ADMIN');

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly policies = signal<LoanPolicy[]>([]);
  readonly busyId = signal<string | null>(null);

  readonly sorted = computed(() =>
    [...this.policies()].sort((a, b) => Number(b.active) - Number(a.active) || a.name.localeCompare(b.name, 'fr')),
  );
  readonly activeCount = computed(() => this.policies().filter((p) => p.active).length);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.loanService.listPolicies().subscribe({
      next: (policies) => {
        this.policies.set(policies);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  terms(p: LoanPolicy): Term[] {
    const terms: Term[] = [];
    if (p.minAmount !== null) terms.push({ icon: 'south', label: 'Montant minimum', value: formatFcfa(p.minAmount) });
    if (p.maxAmount !== null) terms.push({ icon: 'north', label: 'Montant maximum', value: formatFcfa(p.maxAmount) });
    if (p.maxDurationMonths !== null)
      terms.push({ icon: 'schedule', label: 'Durée maximale', value: `${p.maxDurationMonths} mois` });
    if (p.maxActiveLoans !== null)
      terms.push({ icon: 'filter_1', label: 'Prêts actifs maximum', value: `${p.maxActiveLoans}` });
    if (p.minSavingsRequired !== null)
      terms.push({ icon: 'savings', label: 'Épargne minimale', value: formatFcfa(p.minSavingsRequired) });
    if (p.minSeniorityMonths !== null)
      terms.push({ icon: 'badge', label: 'Ancienneté minimale', value: `${p.minSeniorityMonths} mois` });
    if (p.interestRate !== null)
      terms.push({ icon: 'percent', label: "Taux d'intérêt (indicatif)", value: `${p.interestRate} %` });
    return terms;
  }

  create(): void {
    this.dialog
      .open(PolicyFormDialog, { width: '620px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((policy?: LoanPolicy) => {
        if (policy) {
          this.policies.update((list) => [...list, policy]);
          this.snackBar.open(`Politique « ${policy.name} » créée et active`, 'OK', { duration: 4000 });
        }
      });
  }

  deactivate(policy: LoanPolicy): void {
    const data: ConfirmDialogData = {
      title: 'Désactiver cette politique ?',
      message: `« ${policy.name} » ne s'appliquera plus aux nouvelles demandes de prêt. Les prêts existants ne sont pas modifiés. Cette action est définitive.`,
      confirmLabel: 'Désactiver',
      icon: 'block',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '460px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busyId.set(policy.id);
        this.loanService.deactivatePolicy(policy.id).subscribe({
          next: (updated) => {
            this.busyId.set(null);
            this.policies.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
            this.snackBar.open('Politique désactivée', 'OK', { duration: 4000 });
          },
          error: () => {
            this.busyId.set(null);
            this.snackBar.open('La désactivation a échoué.', 'OK', { duration: 5000 });
          },
        });
      });
  }
}
