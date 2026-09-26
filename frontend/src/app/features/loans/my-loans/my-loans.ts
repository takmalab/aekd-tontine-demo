import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, forkJoin, of } from 'rxjs';
import { Loan, LoanPolicy } from '../loan.model';
import { LoanService } from '../loan.service';
import { LoanCard } from '../loan-card/loan-card';
import { LoanRequestDialog, LoanRequestData } from '../loan-request-dialog/loan-request-dialog';

/**
 * « Mes prêts » (CLAUDE.md §6 MEMBRE, §21-§23) : demander un prêt, voir l'évaluation
 * des règles, la décision, le solde restant et l'historique des remboursements.
 */
@Component({
  selector: 'app-my-loans',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, LoanCard],
  templateUrl: './my-loans.html',
  styleUrl: './my-loans.scss',
})
export class MyLoans implements OnInit {
  private readonly loanService = inject(LoanService);
  private readonly dialog = inject(MatDialog);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly noMemberProfile = signal(false);
  readonly loans = signal<Loan[]>([]);
  readonly policies = signal<LoanPolicy[]>([]);

  readonly activePolicies = computed(() => this.policies().filter((p) => p.active));
  readonly ongoing = computed(() =>
    this.loans().filter((l) => l.status === 'REQUESTED' || l.status === 'APPROVED' || l.status === 'IN_PROGRESS'),
  );
  readonly closed = computed(() => this.loans().filter((l) => l.status === 'REPAID' || l.status === 'REJECTED'));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.noMemberProfile.set(false);
    forkJoin({
      loans: this.loanService.mine(),
      policies: this.loanService.listPolicies().pipe(catchError(() => of([] as LoanPolicy[]))),
    }).subscribe({
      next: ({ loans, policies }) => {
        this.loans.set([...loans].sort((a, b) => b.requestedAt.localeCompare(a.requestedAt)));
        this.policies.set(policies);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.noMemberProfile.set(err.status === 403);
        this.error.set(err.status !== 403);
        this.loading.set(false);
      },
    });
  }

  requestLoan(): void {
    const data: LoanRequestData = { activePolicies: this.activePolicies() };
    this.dialog
      .open(LoanRequestDialog, { data, width: '580px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe(() => this.load());
  }
}
