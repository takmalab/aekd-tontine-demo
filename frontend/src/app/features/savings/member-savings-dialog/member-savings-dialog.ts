import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DocumentService } from '../../../core/services/document.service';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import { PAYMENT_OPERATOR_LABELS } from '../../contributions/contribution.model';
import { MemberSavings, SAVINGS_STATUS_LABELS } from '../savings.model';
import { SavingsService } from '../savings.service';

export interface MemberSavingsDialogData {
  memberId: string;
  memberFullName: string;
}

/** Épargne détaillée d'un membre (vue ADMIN / TRESORIER). */
@Component({
  selector: 'app-member-savings-dialog',
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="head">
      <span class="head__icon"><mat-icon>savings</mat-icon></span>
      <div class="head__text">
        <h2 mat-dialog-title class="head__title">Épargne de {{ data.memberFullName }}</h2>
        @if (savings(); as s) {
          <p class="head__subtitle">
            {{ formatFcfa(s.principal) }} versés · {{ s.interest === null ? 'intérêts : taux non fixé' : formatFcfa(s.interest) + " d'intérêts" }}
            · total {{ formatFcfa(s.total) }}
          </p>
        }
      </div>
    </div>
    <mat-dialog-content class="body">
      @if (loading()) {
        <div class="center"><mat-spinner diameter="32" /></div>
      } @else if (error()) {
        <p class="notice"><mat-icon>cloud_off</mat-icon><span>Impossible de charger l'épargne de ce membre.</span></p>
      } @else if (savings(); as s) {
        @if (s.entries.length === 0) {
          <p class="empty">Aucun mouvement d'épargne.</p>
        }
        <ul class="list">
          @for (e of s.entries; track e.id) {
            <li [class.muted]="e.status === 'REJECTED'">
              <div>
                <strong>{{ formatFcfa(e.amount) }}</strong>
                <span [class]="'badge badge--' + e.status.toLowerCase()">{{ statusLabels[e.status] }}</span>
                <div class="meta">
                  {{ e.label }} · {{ fromIsoDate(e.paymentDate) | date: 'd MMM yyyy' }} ·
                  {{ e.operator ? operatorLabels[e.operator] : 'Prélèvement (prêt)' }} · réf. {{ e.reference }}
                </div>
              </div>
              @if (e.interest !== null) {
                <span class="interest">+ {{ formatFcfa(e.interest) }} ({{ e.completeMonths }} mois)</span>
              }
            </li>
          }
        </ul>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end" class="actions">
      <button mat-stroked-button type="button" [disabled]="documents.busy() !== null" (click)="documents.memberStatement(data.memberId)">
        <mat-icon>picture_as_pdf</mat-icon>
        Relevé PDF
      </button>
      <button mat-flat-button type="button" mat-dialog-close>Fermer</button>
    </mat-dialog-actions>
  `,
  styles: `
    @use '../../../shared/styles/dialog' as dlg;
    @include dlg.dialog-base;

    .center {
      display: grid;
      place-items: center;
      padding: 32px;
    }
    .empty {
      color: var(--aekd-gray-muted);
    }
    .list {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;

      li {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        padding: 8px 12px;
        border-radius: 10px;
        background: #faf8f6;
        font-size: 13px;
      }
      li.muted {
        opacity: 0.6;
      }
    }
    .meta {
      margin-top: 2px;
      color: var(--aekd-gray-muted);
    }
    .interest {
      flex: none;
      font-weight: 600;
      color: var(--aekd-green-dark);
    }
    .badge {
      margin-left: 8px;
      padding: 1px 8px;
      border-radius: 999px;
      font-size: 11px;
      font-weight: 700;
    }
    .badge--pending {
      background: var(--aekd-orange-soft);
      color: var(--aekd-brown-dark);
    }
    .badge--validated {
      background: var(--aekd-green-soft);
      color: var(--aekd-green-dark);
    }
    .badge--rejected {
      background: var(--aekd-red-soft);
      color: var(--aekd-red-dark);
    }
  `,
})
export class MemberSavingsDialog implements OnInit {
  private readonly savingsService = inject(SavingsService);
  readonly documents = inject(DocumentService);
  readonly data = inject<MemberSavingsDialogData>(MAT_DIALOG_DATA);

  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;
  readonly statusLabels = SAVINGS_STATUS_LABELS;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly savings = signal<MemberSavings | null>(null);

  ngOnInit(): void {
    this.savingsService.ofMember(this.data.memberId).subscribe({
      next: (savings) => {
        this.savings.set(savings);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
