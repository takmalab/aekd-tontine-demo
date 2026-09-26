import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { ActionMenu, MenuAction } from '../../../shared/components/action-menu/action-menu';
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

type PolicyAction = 'edit' | 'toggle' | 'delete';

/**
 * Politiques de prêt (CLAUDE.md §20) : consultables par tous ; création et
 * désactivation réservées à l'ADMIN (§6 — pas au trésorier).
 */
@Component({
  selector: 'app-loan-policies',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, ActionMenu],
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

  /** Actions du menu (⋮) d'une politique — ADMIN uniquement, cf. gabarit. */
  actionsFor(policy: LoanPolicy): MenuAction<PolicyAction>[] {
    return [
      { label: 'Modifier', icon: 'edit', action: 'edit' },
      policy.active
        ? { label: 'Désactiver', icon: 'block', action: 'toggle' }
        : { label: 'Activer', icon: 'task_alt', action: 'toggle' },
      { label: 'Supprimer', icon: 'delete', action: 'delete', danger: true },
    ];
  }

  onAction(action: PolicyAction, policy: LoanPolicy): void {
    switch (action) {
      case 'edit':
        this.edit(policy);
        break;
      case 'toggle':
        policy.active ? this.deactivate(policy) : this.activate(policy);
        break;
      case 'delete':
        this.remove(policy);
        break;
    }
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

  edit(policy: LoanPolicy): void {
    this.dialog
      .open(PolicyFormDialog, { data: policy, width: '620px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((updated?: LoanPolicy) => {
        if (updated) {
          this.policies.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
          this.snackBar.open('Politique mise à jour', 'OK', { duration: 4000 });
        }
      });
  }

  activate(policy: LoanPolicy): void {
    this.busyId.set(policy.id);
    this.loanService.activatePolicy(policy.id).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.policies.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
        this.snackBar.open('Politique activée', 'OK', { duration: 4000 });
      },
      error: () => {
        this.busyId.set(null);
        this.snackBar.open("L'activation a échoué.", 'OK', { duration: 5000 });
      },
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

  remove(policy: LoanPolicy): void {
    const data: ConfirmDialogData = {
      title: 'Supprimer cette politique ?',
      message: `« ${policy.name} » sera définitivement supprimée. Impossible si elle a déjà été utilisée par un prêt.`,
      confirmLabel: 'Supprimer',
      icon: 'delete',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '460px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busyId.set(policy.id);
        this.loanService.deletePolicy(policy.id).subscribe({
          next: () => {
            this.busyId.set(null);
            this.policies.update((list) => list.filter((p) => p.id !== policy.id));
            this.snackBar.open('Politique supprimée', 'OK', { duration: 4000 });
          },
          error: (error: HttpErrorResponse) => {
            this.busyId.set(null);
            this.snackBar.open(
              error.status === 409
                ? (error.error?.message ?? 'Cette politique a déjà été utilisée par un prêt : elle ne peut pas être supprimée.')
                : 'La suppression a échoué.',
              'OK',
              { duration: 6000 },
            );
          },
        });
      });
  }
}
