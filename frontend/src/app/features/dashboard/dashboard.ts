import { Component, OnInit, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from './dashboard.service';
import { AdminDashboardResponse, MemberDashboardResponse, SessionSummary, StatTile } from './dashboard.model';

function formatFcfa(amount: number): string {
  return `${new Intl.NumberFormat('fr-FR').format(amount)} FCFA`;
}

@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly authService = inject(AuthService);

  readonly loading = signal(true);
  readonly isStaff = this.authService.hasAnyRole(['ADMIN', 'TRESORIER']);
  readonly currentSession = signal<SessionSummary | null>(null);
  readonly tiles = signal<StatTile[]>([]);

  ngOnInit(): void {
    this.dashboardService.get().subscribe((data) => {
      this.currentSession.set(data.currentSession);
      this.tiles.set(
        this.isStaff
          ? this.buildAdminTiles(data as AdminDashboardResponse)
          : this.buildMemberTiles(data as MemberDashboardResponse),
      );
      this.loading.set(false);
    });
  }

  private buildAdminTiles(data: AdminDashboardResponse): StatTile[] {
    return [
      { icon: 'group', value: `${data.memberCount}`, label: 'Membres' },
      { icon: 'checklist', value: `${data.mandatoryContributionsExpected}`, label: 'Cotisations obligatoires attendues' },
      { icon: 'check_circle', value: `${data.paymentsValidated}`, label: 'Paiements validés', tone: 'ok' },
      { icon: 'hourglass_top', value: `${data.paymentsPending}`, label: 'Paiements en attente' },
      { icon: 'warning', value: `${data.lateContributions}`, label: 'Contributions en retard', tone: 'warn' },
      { icon: 'payments', value: formatFcfa(data.amountCollected), label: 'Montant collecté (séance)' },
      { icon: 'account_balance', value: formatFcfa(data.tontineFundBalance), label: 'Fonds commun' },
      { icon: 'savings', value: formatFcfa(data.totalIndividualSavings), label: 'Épargne individuelle totale' },
      { icon: 'request_quote', value: `${data.loanRequestsPending}`, label: 'Demandes de prêt' },
      { icon: 'account_balance_wallet', value: `${data.loansInProgress}`, label: 'Prêts en cours' },
      { icon: 'gavel', value: `${data.activeSanctions}`, label: 'Sanctions actives', tone: 'warn' },
    ];
  }

  private buildMemberTiles(data: MemberDashboardResponse): StatTile[] {
    return [
      { icon: 'checklist', value: `${data.mandatoryContributionsCount}`, label: 'Cotisations obligatoires' },
      { icon: 'check_circle', value: `${data.paidContributionsCount}`, label: 'Payées', tone: 'ok' },
      { icon: 'hourglass_top', value: `${data.pendingContributionsCount}`, label: 'En attente' },
      { icon: 'warning', value: `${data.lateContributionsCount}`, label: 'En retard', tone: 'warn' },
      { icon: 'volunteer_activism', value: `${data.optionalContributionsCount}`, label: 'Facultatives' },
      { icon: 'savings', value: formatFcfa(data.individualSavings), label: 'Mon épargne' },
      { icon: 'account_balance_wallet', value: `${data.activeLoansCount}`, label: 'Prêts en cours' },
      { icon: 'request_quote', value: `${data.pendingLoanRequestsCount}`, label: 'Demandes en attente' },
      { icon: 'payments', value: formatFcfa(data.totalRepaid), label: 'Total remboursé' },
      { icon: 'gavel', value: `${data.activeSanctionsCount}`, label: 'Mes sanctions', tone: 'warn' },
    ];
  }
}
