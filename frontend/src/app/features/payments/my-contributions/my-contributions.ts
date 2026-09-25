import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import {
  ContributionDefinition,
  ContributionMemberLink,
  ContributionPeriod,
  ContributionPeriodStatus,
  ContributionTransaction,
  PAYMENT_OPERATOR_LABELS,
  PERIOD_STATUS_LABELS,
  TRANSACTION_STATUS_LABELS,
} from '../../contributions/contribution.model';
import { ContributionService } from '../../contributions/contribution.service';
import { MemberService } from '../../members/member.service';
import { Session } from '../../sessions/session.model';
import { SessionService } from '../../sessions/session.service';
import { DeclarePaymentDialog, DeclarePaymentData } from '../declare-payment-dialog/declare-payment-dialog';
import { PaymentService } from '../payment.service';
import { canDeclare, displayedPeriodStatus, resolvePeriodStatus } from '../period-status';
import { ProofUploadDialog, ProofUploadData } from '../proof-upload-dialog/proof-upload-dialog';
import { ProofViewerDialog } from '../proof-viewer-dialog/proof-viewer-dialog';

interface PeriodRow {
  definition: ContributionDefinition;
  period: ContributionPeriod;
  status: ContributionPeriodStatus;
  /** Paiements du membre pour cette période, du plus récent au plus ancien. */
  transactions: ContributionTransaction[];
}

interface SessionGroup {
  sessionId: string;
  label: string;
  date: string | null;
  rows: PeriodRow[];
}

type Tab = 'contributions' | 'history';

/**
 * « Mes cotisations » (CLAUDE.md §6 MEMBRE, §13) : pour chaque cotisation ACTIVE
 * dont le membre est participant, ses périodes et sa situation
 * PAID / PENDING / LATE / NOT_PAID, calculée comme le backend
 * (ContributionPeriodStatusService) à partir de ses paiements.
 */
@Component({
  selector: 'app-my-contributions',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './my-contributions.html',
  styleUrl: './my-contributions.scss',
})
export class MyContributions implements OnInit {
  private readonly contributionService = inject(ContributionService);
  private readonly memberService = inject(MemberService);
  private readonly sessionService = inject(SessionService);
  private readonly paymentService = inject(PaymentService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;
  readonly periodStatusLabels = PERIOD_STATUS_LABELS;
  readonly transactionStatusLabels = TRANSACTION_STATUS_LABELS;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;
  readonly canDeclare = canDeclare;

  readonly tab = signal<Tab>('contributions');
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly noMemberProfile = signal(false);
  readonly rows = signal<PeriodRow[]>([]);
  readonly transactions = signal<ContributionTransaction[]>([]);
  private readonly sessions = signal<Session[]>([]);

  readonly groups = computed<SessionGroup[]>(() => {
    const byId = new Map(this.sessions().map((s) => [s.id, s]));
    const groups = new Map<string, SessionGroup>();
    for (const row of this.rows()) {
      const session = byId.get(row.period.sessionId);
      const group = groups.get(row.period.sessionId) ?? {
        sessionId: row.period.sessionId,
        label: row.period.sessionLabel,
        date: session?.date ?? null,
        rows: [],
      };
      group.rows.push(row);
      groups.set(row.period.sessionId, group);
    }
    return [...groups.values()]
      .sort((a, b) => (b.date ?? '').localeCompare(a.date ?? ''))
      .map((g) => ({
        ...g,
        rows: g.rows.sort(
          (a, b) =>
            Number(b.definition.mandatory) - Number(a.definition.mandatory) ||
            (b.definition.amount ?? -1) - (a.definition.amount ?? -1) ||
            a.definition.name.localeCompare(b.definition.name, 'fr'),
        ),
      }));
  });

  readonly counts = computed(() => {
    const c: Record<ContributionPeriodStatus, number> = { PAID: 0, PENDING: 0, LATE: 0, NOT_PAID: 0 };
    for (const row of this.rows()) {
      c[row.status]++;
    }
    return c;
  });

  readonly history = computed(() =>
    [...this.transactions()].sort((a, b) => b.paymentDate.localeCompare(a.paymentDate)),
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.noMemberProfile.set(false);

    this.memberService
      .mine()
      .pipe(
        switchMap((me) =>
          forkJoin({
            definitions: this.contributionService.list(),
            transactions: this.paymentService.mine(),
            sessions: this.sessionService.list(),
          }).pipe(
            switchMap(({ definitions, transactions, sessions }) => {
              const active = definitions.filter((d) => d.status === 'ACTIVE');
              const perDefinition = active.map((definition) =>
                forkJoin({
                  participants: this.contributionService
                    .listParticipants(definition.id)
                    .pipe(catchError(() => of([] as ContributionMemberLink[]))),
                  periods: this.contributionService
                    .listPeriods(definition.id)
                    .pipe(catchError(() => of([] as ContributionPeriod[]))),
                }).pipe(map((links) => ({ definition, ...links }))),
              );
              return (perDefinition.length ? forkJoin(perDefinition) : of([])).pipe(
                map((items) => ({ me, items, transactions, sessions })),
              );
            }),
          ),
        ),
      )
      .subscribe({
        next: ({ me, items, transactions, sessions }) => {
          const rows: PeriodRow[] = [];
          for (const item of items) {
            // Seuls les participants peuvent cotiser (CLAUDE.md §10).
            if (!item.participants.some((p) => p.memberId === me.id)) {
              continue;
            }
            for (const period of item.periods) {
              const raw = resolvePeriodStatus(period.id, period.dueDate, transactions);
              rows.push({
                definition: item.definition,
                period,
                status: displayedPeriodStatus(raw, item.definition.mandatory),
                transactions: transactions
                  .filter((t) => t.contributionPeriodId === period.id)
                  .sort((a, b) => b.paymentDate.localeCompare(a.paymentDate)),
              });
            }
          }
          this.rows.set(rows);
          this.transactions.set(transactions);
          this.sessions.set(sessions);
          this.loading.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.noMemberProfile.set(err.status === 403 || err.status === 404);
          this.error.set(!(err.status === 403 || err.status === 404));
          this.loading.set(false);
        },
      });
  }

  /** Paiement « actif » (en attente ou validé) d'une ligne, s'il existe. */
  activeTransaction(row: PeriodRow): ContributionTransaction | undefined {
    return row.transactions.find((t) => t.status === 'VALIDATED') ?? row.transactions.find((t) => t.status === 'PENDING');
  }

  lastRejected(row: PeriodRow): ContributionTransaction | undefined {
    return canDeclare(row.status) ? row.transactions.find((t) => t.status === 'REJECTED') : undefined;
  }

  declare(row: PeriodRow): void {
    const data: DeclarePaymentData = { definition: row.definition, period: row.period };
    this.dialog
      .open(DeclarePaymentDialog, { data, width: '600px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((transaction?: ContributionTransaction) => {
        if (!transaction) {
          return;
        }
        this.load();
        // Proposer immédiatement de joindre un justificatif (facultatif).
        this.openUpload(transaction, true);
      });
  }

  openUpload(transaction: ContributionTransaction, justDeclared = false): void {
    const data: ProofUploadData = { transaction, justDeclared };
    this.dialog
      .open(ProofUploadDialog, { data, width: '520px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((proof) => {
        if (proof) {
          this.snackBar.open('Justificatif envoyé', 'OK', { duration: 4000 });
        } else if (justDeclared) {
          this.snackBar.open('Paiement déclaré, en attente de validation', 'OK', { duration: 4000 });
        }
      });
  }

  viewProof(transaction: ContributionTransaction): void {
    this.dialog.open(ProofViewerDialog, {
      data: transaction,
      width: '760px',
      maxWidth: 'calc(100vw - 24px)',
      autoFocus: false,
    });
  }
}
