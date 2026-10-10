import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';
import { formatFcfa, toIsoDate } from '../../../shared/utils/format';
import { ContributionDefinition } from '../../contributions/contribution.model';
import { ContributionService } from '../../contributions/contribution.service';
import { Member } from '../../members/member.model';
import { MemberService } from '../../members/member.service';
import { plannedDate, upcomingMonths } from '../tournament-calendar';
import {
  DAYS_OF_WEEK,
  DAY_OF_WEEK_LABELS,
  DayOfWeek,
  Tournament,
  WEEKS_OF_MONTH,
  WEEK_OF_MONTH_LABELS,
  WeekOfMonth,
  ruleLabel,
} from '../tournament.model';
import { TournamentService } from '../tournament.service';

/** Sans données : création. Avec `{ tournament }` : modification (nom, délai, cotisations). */
export interface TournamentFormDialogData {
  tournament?: Tournament;
}

/**
 * Tournoi (docs/decisions.md n° 43) : ordre de passage des membres, règle « n-ième
 * jour de semaine du mois », délai de création et cotisations par défaut.
 */
@Component({
  selector: 'app-tournament-form-dialog',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    DragDropModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './tournament-form-dialog.html',
  styleUrl: './tournament-form-dialog.scss',
})
export class TournamentFormDialog implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly tournamentService = inject(TournamentService);
  private readonly memberService = inject(MemberService);
  private readonly contributionService = inject(ContributionService);
  private readonly dialogRef = inject<MatDialogRef<TournamentFormDialog, Tournament>>(MatDialogRef);
  private readonly data = inject<TournamentFormDialogData | null>(MAT_DIALOG_DATA, { optional: true });

  readonly editing = this.data?.tournament ?? null;

  readonly months = upcomingMonths(18);
  readonly weeks = WEEKS_OF_MONTH;
  readonly weekLabels = WEEK_OF_MONTH_LABELS;
  readonly days = DAYS_OF_WEEK;
  readonly dayLabels = DAY_OF_WEEK_LABELS;
  readonly formatFcfa = formatFcfa;
  readonly ruleLabel = ruleLabel;

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly submitted = signal(false);

  readonly members = signal<Member[]>([]);
  readonly contributions = signal<ContributionDefinition[]>([]);
  /** Ordre de passage (création uniquement). */
  readonly order = signal<Member[]>([]);
  readonly selectedContributions = signal<ReadonlySet<string>>(new Set());

  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    startMonth: [this.months[1] ?? this.months[0], Validators.required],
    weekOfMonth: [1 as WeekOfMonth, Validators.required],
    dayOfWeek: ['SATURDAY' as DayOfWeek, Validators.required],
    leadDays: [null as number | null, [Validators.required, Validators.min(0), Validators.max(365)]],
  });

  private readonly ruleValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** Membres actifs pas encore placés dans l'ordre de passage. */
  readonly available = computed(() => {
    const placed = new Set(this.order().map((m) => m.id));
    return this.members().filter((m) => m.active && !placed.has(m.id));
  });

  /** Aperçu des dates, calculées comme le backend. */
  readonly preview = computed(() => {
    const { startMonth, weekOfMonth, dayOfWeek, leadDays } = this.ruleValue();
    if (!startMonth || !weekOfMonth || !dayOfWeek) {
      return [];
    }
    return this.order().map((member, position) => {
      const date = plannedDate(startMonth, position, weekOfMonth, dayOfWeek);
      const createdOn =
        leadDays !== null && leadDays !== undefined && leadDays >= 0
          ? new Date(date.getFullYear(), date.getMonth(), date.getDate() - leadDays)
          : null;
      return { member, date, createdOn };
    });
  });

  readonly firstDateInPast = computed(() => {
    const first = this.preview()[0];
    return first ? toIsoDate(first.date) < toIsoDate(new Date()) : false;
  });

  ngOnInit(): void {
    if (this.editing) {
      this.form.patchValue({ name: this.editing.name, leadDays: this.editing.leadDays });
      this.selectedContributions.set(new Set(this.editing.contributions.map((c) => c.id)));
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    forkJoin({ members: this.memberService.list(), contributions: this.contributionService.list() }).subscribe({
      next: ({ members, contributions }) => {
        this.members.set([...members].sort((a, b) => a.fullName.localeCompare(b.fullName, 'fr')));
        // Actives, plus celles déjà choisies pour le tournoi modifié (même désactivées depuis).
        const chosen = this.selectedContributions();
        this.contributions.set(
          contributions
            .filter((c) => c.status === 'ACTIVE' || chosen.has(c.id))
            .sort(
              (a, b) =>
                Number(b.mandatory) - Number(a.mandatory) ||
                (b.amount ?? -1) - (a.amount ?? -1) ||
                a.name.localeCompare(b.name, 'fr'),
            ),
        );
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  monthLabel(month: string): string {
    const [y, m] = month.split('-').map(Number);
    return new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(new Date(y, m - 1, 1));
  }

  addMember(member: Member | null): void {
    if (member) {
      this.order.update((list) => [...list, member]);
    }
  }

  addAllActive(): void {
    this.order.update((list) => [...list, ...this.available()]);
  }

  removeMember(index: number): void {
    this.order.update((list) => list.filter((_, i) => i !== index));
  }

  move(index: number, delta: number): void {
    this.order.update((list) => {
      const copy = [...list];
      moveItemInArray(copy, index, index + delta);
      return copy;
    });
  }

  drop(event: CdkDragDrop<Member[]>): void {
    this.move(event.previousIndex, event.currentIndex - event.previousIndex);
  }

  isContributionSelected(id: string): boolean {
    return this.selectedContributions().has(id);
  }

  toggleContribution(id: string, checked: boolean): void {
    this.selectedContributions.update((set) => {
      const next = new Set(set);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  submit(): void {
    this.submitted.set(true);
    if (this.form.invalid || (!this.editing && (this.order().length === 0 || this.firstDateInPast()))) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const contributionDefinitionIds = this.contributions()
      .filter((c) => this.selectedContributions().has(c.id))
      .map((c) => c.id);
    this.saving.set(true);
    this.errorMessage.set(null);

    const save$ = this.editing
      ? this.tournamentService.update(this.editing.id, {
          name: value.name!.trim(),
          leadDays: value.leadDays!,
          contributionDefinitionIds,
        })
      : this.tournamentService.create({
          name: value.name!.trim(),
          startMonth: value.startMonth!,
          weekOfMonth: value.weekOfMonth!,
          dayOfWeek: value.dayOfWeek!,
          leadDays: value.leadDays!,
          memberIds: this.order().map((m) => m.id),
          contributionDefinitionIds,
        });

    save$.subscribe({
      next: (tournament) => this.dialogRef.close(tournament),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.errorMessage.set(
          error.status === 400 || error.status === 409
            ? (error.error?.message ?? 'Les informations saisies sont invalides.')
            : error.status === 403
              ? "Vous n'avez pas les droits pour gérer les tournois."
              : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
        );
      },
    });
  }
}
