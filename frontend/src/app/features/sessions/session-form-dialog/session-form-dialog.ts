import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin, of } from 'rxjs';
import { formatFcfa, fromIsoDate, toIsoDate } from '../../../shared/utils/format';
import {
  CONTRIBUTION_STATUS_LABELS,
  ContributionDefinition,
  FUND_DESTINATION_LABELS,
} from '../../contributions/contribution.model';
import { ContributionService } from '../../contributions/contribution.service';
import { Member } from '../../members/member.model';
import { MemberService } from '../../members/member.service';
import { Session, SessionContributionRequest, SessionRequest } from '../session.model';
import { SessionService } from '../session.service';

/** Cotisation proposée dans la fenêtre : les actives, plus celles déjà rattachées à la séance. */
interface ContributionOption {
  definition: ContributionDefinition;
  /** Déjà rattachée à la séance modifiée. */
  attached: boolean;
}

type ContributionRowForm = FormGroup<{
  selected: FormControl<boolean>;
  dueDate: FormControl<Date | null>;
}>;

/** Sans données : création. Avec `{ session }` : modification de cette séance. */
export interface SessionFormDialogData {
  session?: Session;
}

@Component({
  selector: 'app-session-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './session-form-dialog.html',
  styleUrl: './session-form-dialog.scss',
})
export class SessionFormDialog implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly sessionService = inject(SessionService);
  private readonly memberService = inject(MemberService);
  private readonly contributionService = inject(ContributionService);
  private readonly dialogRef = inject<MatDialogRef<SessionFormDialog, Session>>(MatDialogRef);
  private readonly data = inject<SessionFormDialogData | null>(MAT_DIALOG_DATA, { optional: true });

  /** Séance modifiée, ou null en création. */
  readonly editing = this.data?.session ?? null;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly membersLoading = signal(true);
  readonly membersError = signal(false);
  readonly members = signal<Member[]>([]);

  readonly formatFcfa = formatFcfa;
  readonly destinationLabels = FUND_DESTINATION_LABELS;
  readonly statusLabels = CONTRIBUTION_STATUS_LABELS;

  readonly contributionsLoading = signal(true);
  readonly contributionsError = signal(false);
  readonly contributionOptions = signal<ContributionOption[]>([]);

  readonly form = this.fb.group({
    label: ['', [Validators.required, Validators.maxLength(100)]],
    date: [null as Date | null, Validators.required],
    location: ['', [Validators.required, Validators.maxLength(150)]],
    hostMemberId: [null as string | null, Validators.required],
    beneficiaryMemberIds: [[] as string[], Validators.required],
    // Une ligne par cotisation proposée, dans l'ordre de contributionOptions.
    contributions: this.fb.array<ContributionRowForm>([]),
  });

  /** Cases cochées, recopiées dans un signal pour l'affichage (application sans zone.js). */
  private readonly selectedFlags = signal<boolean[]>([]);

  readonly selectedCount = computed(() => this.selectedFlags().filter(Boolean).length);

  /**
   * Récapitulatif des cotisations obligatoires à montant fixe cochées (CLAUDE.md §9 :
   * chaque cotisation reste distincte, ce total n'est qu'indicatif).
   */
  readonly mandatoryTotal = computed(() =>
    this.contributionOptions().reduce((sum, option, i) => {
      const d = option.definition;
      return this.selectedFlags()[i] && d.mandatory && d.amountMode === 'FIXED' ? sum + (d.amount ?? 0) : sum;
    }, 0),
  );

  constructor() {
    this.form.controls.contributions.valueChanges.subscribe((rows) =>
      this.selectedFlags.set(rows.map((row) => row.selected === true)),
    );
  }

  isSelected(index: number): boolean {
    return this.selectedFlags()[index] === true;
  }

  ngOnInit(): void {
    const session = this.editing;
    if (session) {
      this.form.patchValue({
        label: session.label,
        date: fromIsoDate(session.date),
        location: session.location ?? '',
        hostMemberId: session.hostMember?.memberId ?? null,
        beneficiaryMemberIds: session.beneficiaries.map((b) => b.memberId),
      });
    }
    this.loadMembers();
    this.loadContributions();
  }

  /**
   * Cotisations proposées : les cotisations actives, plus celles déjà rattachées
   * à la séance modifiée (même si elles ne sont plus actives, pour ne pas les
   * retirer sans le vouloir). Rien n'est pré-coché en création : les
   * cotisations diffèrent d'une séance à l'autre.
   */
  loadContributions(): void {
    this.contributionsLoading.set(true);
    this.contributionsError.set(false);
    forkJoin({
      definitions: this.contributionService.list(),
      attached: this.editing ? this.sessionService.contributionsOf(this.editing.id) : of([]),
    }).subscribe({
      next: ({ definitions, attached }) => {
        const dueDates = new Map(attached.map((c) => [c.definition.id, c.period.dueDate]));
        const options: ContributionOption[] = definitions
          .filter((d) => d.status === 'ACTIVE' || dueDates.has(d.id))
          .map((definition) => ({ definition, attached: dueDates.has(definition.id) }))
          .sort(
            (a, b) =>
              Number(b.definition.mandatory) - Number(a.definition.mandatory) ||
              (b.definition.amount ?? -1) - (a.definition.amount ?? -1) ||
              a.definition.name.localeCompare(b.definition.name, 'fr'),
          );

        const rows = this.form.controls.contributions;
        rows.clear({ emitEvent: false });
        for (const option of options) {
          const dueDate = dueDates.get(option.definition.id);
          rows.push(
            this.fb.group({
              selected: this.fb.nonNullable.control(option.attached),
              dueDate: this.fb.control<Date | null>(dueDate ? fromIsoDate(dueDate) : null),
            }),
            { emitEvent: false },
          );
        }
        this.contributionOptions.set(options);
        this.selectedFlags.set(options.map((o) => o.attached));
        this.contributionsLoading.set(false);
      },
      error: () => {
        this.contributionsError.set(true);
        this.contributionsLoading.set(false);
      },
    });
  }

  loadMembers(): void {
    this.membersLoading.set(true);
    this.membersError.set(false);
    this.memberService.list().subscribe({
      next: (members) => {
        // Seuls les membres actifs sont proposés, plus ceux déjà choisis pour la
        // séance modifiée (pour ne pas les perdre s'ils sont devenus inactifs).
        const current = new Set([
          this.editing?.hostMember?.memberId,
          ...(this.editing?.beneficiaries.map((b) => b.memberId) ?? []),
        ]);
        this.members.set(
          members
            .filter((m) => m.active || current.has(m.id))
            .sort((a, b) => a.fullName.localeCompare(b.fullName, 'fr')),
        );
        this.membersLoading.set(false);
      },
      error: () => {
        this.membersError.set(true);
        this.membersLoading.set(false);
      },
    });
  }

  memberName(id: string): string {
    return this.members().find((m) => m.id === id)?.fullName ?? '';
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { label, date, location, hostMemberId, beneficiaryMemberIds } = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);

    const request: SessionRequest = {
      label: label!.trim(),
      date: toIsoDate(date!),
      location: location!.trim(),
      hostMemberId: hostMemberId!,
      beneficiaryMemberIds: beneficiaryMemberIds!,
      // Liste envoyée seulement si elle a pu être chargée : sinon, en modification,
      // une liste vide retirerait toutes les cotisations de la séance.
      contributions: this.contributionsError() ? undefined : this.selectedContributions(),
    };
    const save$ = this.editing
      ? this.sessionService.update(this.editing.id, request)
      : this.sessionService.create(request);

    save$.subscribe({
      next: (session) => this.dialogRef.close(session),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.errorMessage.set(
          error.status === 400 || error.status === 409
            ? (error.error?.message ?? 'Les informations saisies sont invalides.')
            : error.status === 403
              ? `Vous n'avez pas les droits pour ${this.editing ? 'modifier' : 'créer'} une séance.`
              : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
        );
      },
    });
  }

  private selectedContributions(): SessionContributionRequest[] {
    const rows = this.form.controls.contributions.getRawValue();
    return this.contributionOptions()
      .map((option, i) => ({ option, row: rows[i] }))
      .filter(({ row }) => row?.selected)
      .map(({ option, row }) => ({
        contributionDefinitionId: option.definition.id,
        dueDate: row.dueDate ? toIsoDate(row.dueDate) : null,
      }));
  }
}
