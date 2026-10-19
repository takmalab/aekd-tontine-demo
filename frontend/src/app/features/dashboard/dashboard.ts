import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { formatFcfa, fromIsoDate } from '../../shared/utils/format';
import { DashboardService } from './dashboard.service';
import {
  AdminDashboardResponse,
  DashboardShortcut,
  MemberDashboardResponse,
  SessionSummary,
  StatTile,
} from './dashboard.model';
import { ThemeService } from '../../core/services/theme.service';

/** « 1 paiement », « 2 paiements », « 0 paiement » : singulier jusqu'à 1 (U5). */
function plural(count: number, singular: string, pluralForm: string): string {
  return Math.abs(count) <= 1 ? singular : pluralForm;
}

/**
 * Tableau de bord (CLAUDE.md §25). Chaque tuile mène à la page filtrée correspondante et des
 * raccourcis donnent accès aux actions courantes (test de bout en bout du 2026-10-17, U1).
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, MatCardModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  /** Nom, logo et slogan de l'association (thème, décision n° 72). */
  protected readonly theme = inject(ThemeService);
  private readonly dashboardService = inject(DashboardService);
  private readonly authService = inject(AuthService);

  readonly fromIsoDate = fromIsoDate;
  readonly loading = signal(true);
  readonly isStaff = this.authService.hasAnyRole(['ADMIN', 'TRESORIER']);
  readonly currentSession = signal<SessionSummary | null>(null);
  readonly tiles = signal<StatTile[]>([]);
  readonly shortcuts = signal<DashboardShortcut[]>([]);

  ngOnInit(): void {
    this.dashboardService.get().subscribe((data) => {
      this.currentSession.set(data.currentSession);
      if (this.isStaff) {
        const admin = data as AdminDashboardResponse;
        this.tiles.set(this.buildAdminTiles(admin));
        this.shortcuts.set(this.buildAdminShortcuts(admin));
      } else {
        const member = data as MemberDashboardResponse;
        this.tiles.set(this.buildMemberTiles(member));
        this.shortcuts.set(this.buildMemberShortcuts(member));
      }
      this.loading.set(false);
    });
  }

  /** Lien vers la séance actuelle, sinon vers la liste des séances. */
  private sessionLink(): string[] {
    const session = this.currentSession();
    return session ? ['/sessions', session.id] : ['/sessions'];
  }

  private buildAdminTiles(d: AdminDashboardResponse): StatTile[] {
    return [
      { icon: 'group', value: `${d.memberCount}`, label: plural(d.memberCount, 'Membre', 'Membres'), link: ['/members'] },
      {
        icon: 'checklist',
        value: `${d.mandatoryContributionsExpected}`,
        label: plural(d.mandatoryContributionsExpected, 'Cotisation obligatoire attendue', 'Cotisations obligatoires attendues'),
        link: this.sessionLink(),
      },
      {
        icon: 'check_circle',
        value: `${d.paymentsValidated}`,
        label: plural(d.paymentsValidated, 'Paiement validé', 'Paiements validés'),
        tone: d.paymentsValidated > 0 ? 'ok' : undefined,
        link: this.sessionLink(),
      },
      {
        icon: 'hourglass_top',
        value: `${d.paymentsPending}`,
        label: plural(d.paymentsPending, 'Paiement en attente', 'Paiements en attente'),
        link: ['/payments'],
      },
      {
        icon: 'warning',
        value: `${d.lateContributions}`,
        label: plural(d.lateContributions, 'Cotisation en retard', 'Cotisations en retard'),
        tone: d.lateContributions > 0 ? 'warn' : undefined,
        link: ['/members'],
        query: { situation: 'LATE' },
      },
      { icon: 'payments', value: formatFcfa(d.amountCollected), label: 'Montant collecté (séance)', link: this.sessionLink() },
      { icon: 'account_balance', value: formatFcfa(d.tontineFundBalance), label: 'Fonds commun (reste à remettre)', link: ['/funds'] },
      { icon: 'redeem', value: formatFcfa(d.tontineFundRemitted), label: 'Cagnottes remises aux bénéficiaires', link: ['/funds'] },
      { icon: 'savings', value: formatFcfa(d.totalIndividualSavings), label: 'Épargne individuelle totale', link: ['/savings'] },
      { icon: 'price_check', value: formatFcfa(d.availableForLending), label: 'Disponible pour prêter', link: ['/funds'] },
      {
        icon: 'request_quote',
        value: `${d.loanRequestsPending}`,
        label: plural(d.loanRequestsPending, 'Demande de prêt à traiter', 'Demandes de prêt à traiter'),
        link: ['/loans'],
        query: { tab: 'REQUESTED' },
      },
      {
        icon: 'account_balance_wallet',
        value: `${d.loansInProgress}`,
        label: plural(d.loansInProgress, 'Prêt en cours', 'Prêts en cours'),
        link: ['/loans'],
        query: { tab: 'ONGOING' },
      },
      {
        icon: 'gavel',
        value: `${d.activeSanctions}`,
        label: plural(d.activeSanctions, 'Sanction active', 'Sanctions actives'),
        tone: d.activeSanctions > 0 ? 'warn' : undefined,
        link: ['/sanctions'],
      },
    ];
  }

  private buildMemberTiles(d: MemberDashboardResponse): StatTile[] {
    const contributions = ['/my-contributions'];
    return [
      {
        icon: 'checklist',
        value: `${d.mandatoryContributionsCount}`,
        label: plural(d.mandatoryContributionsCount, 'Cotisation obligatoire', 'Cotisations obligatoires'),
        link: contributions,
      },
      {
        icon: 'check_circle',
        value: `${d.paidContributionsCount}`,
        label: plural(d.paidContributionsCount, 'Payée', 'Payées'),
        tone: d.paidContributionsCount > 0 ? 'ok' : undefined,
        link: contributions,
      },
      { icon: 'hourglass_top', value: `${d.pendingContributionsCount}`, label: 'En attente de validation', link: contributions },
      {
        icon: 'warning',
        value: `${d.lateContributionsCount}`,
        label: 'En retard',
        tone: d.lateContributionsCount > 0 ? 'warn' : undefined,
        link: contributions,
      },
      {
        icon: 'volunteer_activism',
        value: `${d.optionalContributionsCount}`,
        label: plural(d.optionalContributionsCount, 'Facultative', 'Facultatives'),
        link: contributions,
      },
      { icon: 'savings', value: formatFcfa(d.individualSavings), label: 'Mon épargne', link: ['/my-savings'] },
      {
        icon: 'account_balance_wallet',
        value: `${d.activeLoansCount}`,
        label: plural(d.activeLoansCount, 'Prêt en cours', 'Prêts en cours'),
        link: ['/my-loans'],
      },
      {
        icon: 'request_quote',
        value: `${d.pendingLoanRequestsCount}`,
        label: plural(d.pendingLoanRequestsCount, 'Demande de prêt en attente', 'Demandes de prêt en attente'),
        link: ['/my-loans'],
      },
      { icon: 'payments', value: formatFcfa(d.totalRepaid), label: 'Total remboursé', link: ['/my-loans'] },
      {
        icon: 'gavel',
        value: `${d.activeSanctionsCount}`,
        label: plural(d.activeSanctionsCount, 'Ma sanction', 'Mes sanctions'),
        tone: d.activeSanctionsCount > 0 ? 'warn' : undefined,
        link: ['/sanctions'],
      },
    ];
  }

  private buildAdminShortcuts(d: AdminDashboardResponse): DashboardShortcut[] {
    return [
      { icon: 'fact_check', label: 'Valider les paiements', link: ['/payments'], badge: d.paymentsPending },
      { icon: 'request_quote', label: 'Traiter les prêts', link: ['/loans'], query: { tab: 'REQUESTED' }, badge: d.loanRequestsPending },
      { icon: 'warning', label: 'Membres en retard', link: ['/members'], query: { situation: 'LATE' }, badge: d.lateContributions },
      { icon: 'add', label: 'Nouvelle séance', link: ['/sessions'] },
    ];
  }

  private buildMemberShortcuts(d: MemberDashboardResponse): DashboardShortcut[] {
    return [
      { icon: 'send_money', label: 'Cotiser', link: ['/my-contributions'], badge: d.lateContributionsCount, primary: true },
      { icon: 'add_card', label: 'Demander un prêt', link: ['/my-loans'] },
      { icon: 'savings', label: 'Épargner', link: ['/my-savings'] },
    ];
  }
}
