import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { DocumentService } from '../../../core/services/document.service';
import { FilterChips, FilterOption } from '../../../shared/components/filter-chips/filter-chips';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import { PAYMENT_OPERATOR_LABELS } from '../../contributions/contribution.model';
import { ProofUploadData, ProofUploadDialog } from '../../payments/proof-upload-dialog/proof-upload-dialog';
import { ProofViewerData, ProofViewerDialog } from '../../payments/proof-viewer-dialog/proof-viewer-dialog';
import { DepositDialog } from '../deposit-dialog/deposit-dialog';
import {
  MemberSavings,
  SAVINGS_SOURCE_LABELS,
  SAVINGS_STATUS_LABELS,
  SavingsEntry,
  SavingsStatus,
  formatMonthlyRate,
} from '../savings.model';
import { SavingsService } from '../savings.service';

type EntryFilter = 'ALL' | SavingsStatus;

/**
 * « Mon épargne » (docs/decisions.md n° 39) : épargne validée, intérêts
 * acquis (taux mensuel, intérêts simples par mois complet), versements en
 * attente, historique, et bouton « Épargner ».
 */
@Component({
  selector: 'app-my-savings',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule, FilterChips],
  templateUrl: './my-savings.html',
  styleUrl: './my-savings.scss',
})
export class MySavings implements OnInit {
  private readonly savingsService = inject(SavingsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  readonly documents = inject(DocumentService);

  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;
  readonly formatMonthlyRate = formatMonthlyRate;
  readonly statusLabels = SAVINGS_STATUS_LABELS;
  readonly sourceLabels = SAVINGS_SOURCE_LABELS;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly noMemberProfile = signal(false);
  readonly savings = signal<MemberSavings | null>(null);
  readonly filter = signal<EntryFilter>('ALL');

  readonly filterOptions = computed<FilterOption<EntryFilter>[]>(() => {
    const entries = this.savings()?.entries ?? [];
    const count = (s: SavingsStatus) => entries.filter((e) => e.status === s).length;
    return [
      { value: 'ALL', label: 'Tous', count: entries.length },
      { value: 'VALIDATED', label: 'Validés', count: count('VALIDATED') },
      { value: 'PENDING', label: 'En attente', count: count('PENDING') },
      { value: 'REJECTED', label: 'Rejetés', count: count('REJECTED') },
    ];
  });

  readonly visibleEntries = computed(() => {
    const entries = this.savings()?.entries ?? [];
    const f = this.filter();
    return f === 'ALL' ? entries : entries.filter((e) => e.status === f);
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.noMemberProfile.set(false);
    this.savingsService.mine().subscribe({
      next: (savings) => {
        this.savings.set(savings);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.noMemberProfile.set(err.status === 403);
        this.error.set(err.status !== 403);
        this.loading.set(false);
      },
    });
  }

  save(): void {
    this.dialog
      .open(DepositDialog, { width: '580px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((entry?: SavingsEntry) => {
        if (!entry) {
          return;
        }
        this.load();
        this.attachProof(entry, true);
      });
  }

  attachProof(entry: SavingsEntry, justDeclared = false): void {
    const data: ProofUploadData = { deposit: entry, justDeclared };
    this.dialog
      .open(ProofUploadDialog, { data, width: '520px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.load();
        }
        this.snackBar.open(
          result
            ? 'Justificatif envoyé'
            : justDeclared
              ? 'Versement déclaré, en attente de validation par le trésorier'
              : 'Aucun justificatif envoyé',
          'OK',
          { duration: 4000 },
        );
      });
  }

  viewProof(entry: SavingsEntry): void {
    const data: ProofViewerData = { deposit: entry };
    this.dialog.open(ProofViewerDialog, { data, width: '760px', maxWidth: 'calc(100vw - 24px)', autoFocus: false });
  }
}
