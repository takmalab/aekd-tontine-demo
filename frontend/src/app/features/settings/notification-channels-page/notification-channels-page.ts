import { HttpClient } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../../environments/environment';
import { NOTIFICATION_STYLE, NotificationType } from '../../../core/models/notification.model';

interface ChannelRow {
  type: NotificationType;
  emailEnabled: boolean;
  smsEnabled: boolean;
}

interface ChannelsResponse {
  channels: ChannelRow[];
  emailLive: boolean;
  smsLive: boolean;
  smsStatus: string;
}

/** Libellé et destinataires de chaque type de notification. */
const TYPE_INFO: Partial<Record<NotificationType, { label: string; group: 'members' | 'staff' }>> = {
  SESSION_CREATED: { label: 'Nouvelle séance', group: 'members' },
  SESSION_CONTRIBUTION_ADDED: { label: 'Cotisation ajoutée à une séance', group: 'members' },
  CONTRIBUTION_REMINDER: { label: 'Rappel de cotisation', group: 'members' },
  PAYMENT_VALIDATED: { label: 'Paiement de cotisation validé', group: 'members' },
  PAYMENT_REJECTED: { label: 'Paiement de cotisation rejeté', group: 'members' },
  SAVINGS_DEPOSIT_VALIDATED: { label: "Versement d'épargne validé", group: 'members' },
  SAVINGS_DEPOSIT_REJECTED: { label: "Versement d'épargne rejeté", group: 'members' },
  LOAN_APPROVED: { label: 'Prêt approuvé', group: 'members' },
  LOAN_REJECTED: { label: 'Prêt rejeté', group: 'members' },
  LOAN_REPAYMENT_VALIDATED: { label: 'Remboursement validé', group: 'members' },
  LOAN_REPAYMENT_REJECTED: { label: 'Remboursement rejeté', group: 'members' },
  LOAN_REPAYMENT_RECORDED: { label: 'Remboursement enregistré par le trésorier', group: 'members' },
  LOAN_GUARANTEE_REQUESTED: { label: "Demande d'aval (avaliste)", group: 'members' },
  LOAN_GUARANTEE_ACCEPTED: { label: 'Aval accepté', group: 'members' },
  LOAN_GUARANTEE_DECLINED: { label: 'Aval refusé', group: 'members' },
  LOAN_DEDUCTION_RECORDED: { label: 'Retenue pour un prêt', group: 'members' },
  SANCTION_APPLIED: { label: 'Sanction appliquée', group: 'members' },
  SANCTION_CANCELLED: { label: 'Sanction annulée', group: 'members' },
  SANCTION_PAYMENT_VALIDATED: { label: 'Paiement de sanction validé', group: 'members' },
  SANCTION_PAYMENT_REJECTED: { label: 'Paiement de sanction rejeté', group: 'members' },
  SESSION_PAYOUT_RECEIVED: { label: 'Cagnotte remise', group: 'members' },
  MEMBER_ACTIVITY: { label: "Activité d'un autre membre", group: 'members' },
  PAYMENT_DECLARED: { label: 'Paiement de cotisation à valider', group: 'staff' },
  LOAN_REPAYMENT_DECLARED: { label: 'Remboursement à valider', group: 'staff' },
  SAVINGS_DEPOSIT_DECLARED: { label: "Versement d'épargne à valider", group: 'staff' },
  SANCTION_PAYMENT_DECLARED: { label: 'Paiement de sanction à valider', group: 'staff' },
  LOAN_REQUESTED: { label: 'Nouvelle demande de prêt', group: 'staff' },
  USER_REGISTERED: { label: 'Inscription à valider', group: 'staff' },
  TOURNAMENT_SESSION_GENERATED: { label: 'Séance de tournoi créée', group: 'staff' },
  TOURNAMENT_COMPLETED: { label: 'Tournoi terminé', group: 'staff' },
};

/**
 * « Notifications e-mail et SMS » (super administrateur, decisions.md n° 58) :
 * pour chaque type, l'e-mail et le SMS s'activent séparément. La notification
 * dans l'application est toujours envoyée.
 */
@Component({
  selector: 'app-notification-channels-page',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatSlideToggleModule],
  templateUrl: './notification-channels-page.html',
  styleUrls: ['../sms-settings-page/sms-settings-page.scss', './notification-channels-page.scss'],
})
export class NotificationChannelsPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  private readonly url = `${environment.apiUrl}/settings/notifications`;

  readonly styles = NOTIFICATION_STYLE;
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly saving = signal(false);
  readonly status = signal<Omit<ChannelsResponse, 'channels'> | null>(null);
  readonly rows = signal<ChannelRow[]>([]);
  private saved: string = '[]';

  readonly dirty = computed(() => JSON.stringify(this.rows()) !== this.saved);
  readonly groups = computed(() => [
    { title: 'Pour les membres', rows: this.rows().filter((r) => this.info(r.type).group === 'members') },
    { title: 'Pour le bureau (trésorier, administrateurs)', rows: this.rows().filter((r) => this.info(r.type).group === 'staff') },
  ]);
  readonly smsCount = computed(() => this.rows().filter((r) => r.smsEnabled).length);

  ngOnInit(): void {
    this.load();
  }

  info(type: NotificationType): { label: string; group: 'members' | 'staff' } {
    return TYPE_INFO[type] ?? { label: type, group: 'members' };
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.http.get<ChannelsResponse>(this.url).subscribe({
      next: (response) => {
        this.apply(response);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  toggle(type: NotificationType, channel: 'emailEnabled' | 'smsEnabled', value: boolean): void {
    this.rows.update((rows) => rows.map((r) => (r.type === type ? { ...r, [channel]: value } : r)));
  }

  setAll(channel: 'emailEnabled' | 'smsEnabled', value: boolean): void {
    this.rows.update((rows) => rows.map((r) => ({ ...r, [channel]: value })));
  }

  save(): void {
    this.saving.set(true);
    this.http.put<ChannelsResponse>(this.url, { channels: this.rows() }).subscribe({
      next: (response) => {
        this.saving.set(false);
        this.apply(response);
        this.snackBar.open('Notifications enregistrées', 'OK', { duration: 4000 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open("Les réglages n'ont pas pu être enregistrés.", 'OK', { duration: 6000 });
      },
    });
  }

  private apply(response: ChannelsResponse): void {
    const { channels, ...status } = response;
    this.status.set(status);
    this.rows.set(channels);
    this.saved = JSON.stringify(channels);
  }
}
