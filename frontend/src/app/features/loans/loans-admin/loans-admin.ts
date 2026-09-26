import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { formatFcfa } from '../../../shared/utils/format';
import { Loan, LoanBalance, LoanStatus } from '../loan.model';
import { LoanService } from '../loan.service';
import { LoanCard } from '../loan-card/loan-card';
import { LoanDecisionDialog, LoanDecisionData } from '../loan-decision-dialog/loan-decision-dialog';
import { RepaymentDialog, RepaymentData } from '../repayment-dialog/repayment-dialog';

type LoanFilter = 'ALL' | 'REQUESTED' | 'ONGOING' | 'REPAID' | 'REJECTED';

const FILTER_STATUSES: Record<Exclude<LoanFilter, 'ALL'>, LoanStatus[]> = {
  REQUESTED: ['REQUESTED'],
  ONGOING: ['APPROVED', 'IN_PROGRESS'],
  REPAID: ['REPAID'],
  REJECTED: ['REJECTED'],
};

/**
 * Prêts, vue ADMIN et TRESORIER (CLAUDE.md §21-§23). Tous deux consultent ;
 * seul le TRESORIER approuve, rejette et enregistre les remboursements (§6).
 */
@Component({
  selector: 'app-loans-admin',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, LoanCard],
  templateUrl: './loans-admin.html',
  styleUrl: './loans-admin.scss',
})
export class LoansAdmin implements OnInit {
  private readonly loanService = inject(LoanService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Décisions et remboursements : TRESORIER uniquement (pas l'ADMIN). */
  readonly isTreasurer = this.authService.hasRole('TRESORIER');

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly loans = signal<Loan[]>([]);
  readonly filter = signal<LoanFilter>('REQUESTED');

  readonly filters: { value: LoanFilter; label: string }[] = [
    { value: 'REQUESTED', label: 'À traiter' },
    { value: 'ONGOING', label: 'En cours' },
    { value: 'REPAID', label: 'Remboursés' },
    { value: 'REJECTED', label: 'Rejetés' },
    { value: 'ALL', label: 'Tous' },
  ];

  readonly counts = computed(() => {
    const all = this.loans();
    const c = { ALL: all.length } as Record<LoanFilter, number>;
    for (const [key, statuses] of Object.entries(FILTER_STATUSES)) {
      c[key as LoanFilter] = all.filter((l) => statuses.includes(l.status)).length;
    }
    return c;
  });

  readonly visibleLoans = computed(() => {
    const f = this.filter();
    return f === 'ALL' ? this.loans() : this.loans().filter((l) => FILTER_STATUSES[f].includes(l.status));
  });

  readonly outstanding = computed(() =>
    this.loans()
      .filter((l) => l.status === 'APPROVED' || l.status === 'IN_PROGRESS')
      .reduce((sum, l) => sum + (l.approvedAmount ?? 0), 0),
  );
  readonly formatFcfa = formatFcfa;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.loanService.list().subscribe({
      next: (loans) => {
        this.loans.set([...loans].sort((a, b) => b.requestedAt.localeCompare(a.requestedAt)));
        // Rien à traiter : afficher les prêts en cours plutôt qu'une liste vide.
        if (this.filter() === 'REQUESTED' && !loans.some((l) => l.status === 'REQUESTED')) {
          this.filter.set('ONGOING');
        }
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  private replace(updated: Loan): void {
    this.loans.update((list) => list.map((l) => (l.id === updated.id ? updated : l)));
  }

  decide(loan: Loan, decision: 'approve' | 'reject'): void {
    const data: LoanDecisionData = { loan, decision };
    this.dialog
      .open(LoanDecisionDialog, { data, width: '580px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((updated?: Loan) => {
        if (!updated) {
          return;
        }
        this.replace(updated);
        this.snackBar.open(
          updated.status === 'REJECTED'
            ? 'Demande rejetée'
            : `Prêt approuvé : ${formatFcfa(updated.approvedAmount)} sur ${updated.approvedDurationMonths} mois`,
          'OK',
          { duration: 4000 },
        );
      });
  }

  repay(event: { loan: Loan; balance: LoanBalance }): void {
    const data: RepaymentData = event;
    this.dialog
      .open(RepaymentDialog, { data, width: '580px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((repayment) => {
        if (!repayment) {
          return;
        }
        // Le statut (IN_PROGRESS / REPAID) est recalculé par le backend : on relit le prêt.
        this.loanService.get(event.loan.id).subscribe((updated) => {
          this.replace(updated);
          this.snackBar.open(
            updated.status === 'REPAID'
              ? 'Remboursement enregistré : prêt entièrement remboursé'
              : `Remboursement de ${formatFcfa(repayment.amount)} enregistré`,
            'OK',
            { duration: 4000 },
          );
        });
      });
  }
}
