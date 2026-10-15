import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { formatFcfa } from '../../../shared/utils/format';
import {
  APPLIED_STATUS_LABELS,
  AppliedSanction,
  SANCTION_PAYMENT_STATUS_LABELS,
  SANCTION_TYPE_LABELS,
  SanctionPayment,
  sanctionRemaining,
} from '../sanction.model';
import { SanctionPaymentDialog } from '../payment-dialog/payment-dialog';
import { SanctionService } from '../sanction.service';

/**
 * « Mes sanctions » (CLAUDE.md §6 MEMBRE) : sanctions réellement appliquées au membre
 * connecté, et paiement des sanctions monétaires (decisions.md n° 50).
 */
@Component({
  selector: 'app-my-sanctions',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './my-sanctions.html',
  styleUrl: './my-sanctions.scss',
})
export class MySanctions implements OnInit {
  private readonly sanctionService = inject(SanctionService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly typeLabels = SANCTION_TYPE_LABELS;
  readonly statusLabels = APPLIED_STATUS_LABELS;
  readonly formatFcfa = formatFcfa;
  readonly remaining = sanctionRemaining;
  readonly paymentStatusLabels = SANCTION_PAYMENT_STATUS_LABELS;
  readonly payments = signal<SanctionPayment[]>([]);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly noMemberProfile = signal(false);
  readonly sanctions = signal<AppliedSanction[]>([]);

  readonly active = computed(() => this.sanctions().filter((s) => s.status === 'APPLIED'));
  readonly activeAmount = computed(() => this.active().reduce((sum, s) => sum + (s.amount ?? 0), 0));
  readonly remainingAmount = computed(() => this.active().reduce((sum, s) => sum + sanctionRemaining(s), 0));

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
        this.sanctionService.myPayments().subscribe({ next: (p) => this.payments.set(p) });
      },
      error: (err: HttpErrorResponse) => {
        this.noMemberProfile.set(err.status === 403);
        this.error.set(err.status !== 403);
        this.loading.set(false);
      },
    });
  }

  pay(sanction: AppliedSanction): void {
    this.dialog
      .open(SanctionPaymentDialog, { data: sanction, width: '560px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((payment?: SanctionPayment) => {
        if (payment) {
          this.snackBar.open('Paiement déclaré : il sera compté après validation par le trésorier.', 'OK', { duration: 5000 });
          this.load();
        }
      });
  }
}
