import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { firstValueFrom } from 'rxjs';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { formatFcfa, fromIsoDate, toIsoDate } from '../../../shared/utils/format';
import { Member } from '../../members/member.model';
import { MemberService } from '../../members/member.service';
import { Session } from '../../sessions/session.model';
import { SessionService } from '../../sessions/session.service';
import {
  AmountMode,
  ContributionDefinition,
  FREQUENCY_LABELS,
  FUND_DESTINATION_LABELS,
  FundDestination,
  VISIBILITY_LABELS,
  Visibility,
} from '../contribution.model';
import { ContributionService } from '../contribution.service';

interface StepMeta {
  title: string;
  short: string;
  icon: string;
}

/**
 * Assistant de création d'une cotisation (CLAUDE.md §31). L'étape « Sanctions »
 * est volontairement absente : les règles de sanction restent à valider (§38).
 *
 * Les étapes 1 à 8 ne font aucun appel serveur. Au récapitulatif, « Enregistrer
 * le brouillon » crée la cotisation (DRAFT) puis ses participants, bénéficiaires
 * et son rattachement à une séance. L'activation est une action distincte et
 * confirmée, à la dernière étape.
 */
@Component({
  selector: 'app-contribution-wizard',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    NgTemplateOutlet,
    MatStepperModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './contribution-wizard.html',
  styleUrl: './contribution-wizard.scss',
})
export class ContributionWizard implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly contributionService = inject(ContributionService);
  private readonly memberService = inject(MemberService);
  private readonly sessionService = inject(SessionService);
  private readonly dialog = inject(MatDialog);

  @ViewChild('stepper') stepper!: MatStepper;

  readonly steps: StepMeta[] = [
    { title: 'Informations générales', short: 'Infos', icon: 'edit_note' },
    { title: 'Montant et fréquence', short: 'Montant', icon: 'payments' },
    { title: 'Obligatoire ou facultative', short: 'Caractère', icon: 'rule' },
    { title: 'Visibilité', short: 'Visibilité', icon: 'visibility' },
    { title: 'Participants', short: 'Participants', icon: 'group' },
    { title: 'Bénéficiaires', short: 'Bénéficiaires', icon: 'redeem' },
    { title: 'Séance et échéance', short: 'Séance', icon: 'event' },
    { title: 'Destination des fonds', short: 'Destination', icon: 'account_balance' },
    { title: 'Récapitulatif', short: 'Récapitulatif', icon: 'fact_check' },
    { title: 'Activation', short: 'Activation', icon: 'rocket_launch' },
  ];

  readonly frequencyLabels = FREQUENCY_LABELS;
  readonly destinationLabels = FUND_DESTINATION_LABELS;
  readonly visibilityLabels = VISIBILITY_LABELS;
  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;

  // ---------- Formulaires (un groupe par étape) ----------
  readonly infoForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.maxLength(1000)]],
  });

  readonly amountForm = this.fb.group({
    amountMode: ['FIXED' as AmountMode, Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
  });

  readonly natureForm = this.fb.group({
    mandatory: [null as boolean | null, Validators.required],
  });

  readonly visibilityForm = this.fb.group({
    visibility: [null as Visibility | null, Validators.required],
  });

  readonly participantsForm = this.fb.group({ memberIds: [[] as string[]] });
  readonly beneficiariesForm = this.fb.group({ memberIds: [[] as string[]] });

  readonly sessionForm = this.fb.group({
    sessionId: [null as string | null],
    dueDate: [{ value: null as Date | null, disabled: true }],
  });

  readonly destinationForm = this.fb.group({
    fundDestination: [null as FundDestination | null, Validators.required],
  });

  private readonly amountMode = toSignal(this.amountForm.controls.amountMode.valueChanges, {
    initialValue: this.amountForm.controls.amountMode.value,
  });
  readonly isFixed = computed(() => this.amountMode() === 'FIXED');

  // ---------- Données de référence ----------
  readonly members = signal<Member[]>([]);
  readonly sessions = signal<Session[]>([]);
  readonly referenceLoading = signal(true);
  readonly referenceError = signal(false);

  // ---------- État de l'enregistrement ----------
  readonly currentIndex = signal(0);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly created = signal<ContributionDefinition | null>(null);
  readonly activating = signal(false);
  readonly activated = signal(false);
  readonly activationError = signal<string | null>(null);
  private readonly doneParticipants = new Set<string>();
  private readonly doneBeneficiaries = new Set<string>();
  private periodDone = false;

  readonly progress = computed(() => ((this.currentIndex() + 1) / this.steps.length) * 100);

  constructor() {
    // Le montant n'existe que pour une cotisation à montant fixe.
    this.amountForm.controls.amountMode.valueChanges.subscribe((mode) => {
      const amount = this.amountForm.controls.amount;
      if (mode === 'FIXED') {
        amount.enable();
      } else {
        amount.reset(null);
        amount.disable();
      }
    });
    // L'échéance n'a de sens que si une séance est choisie.
    this.sessionForm.controls.sessionId.valueChanges.subscribe((sessionId) => {
      const due = this.sessionForm.controls.dueDate;
      if (sessionId) {
        due.enable();
      } else {
        due.reset(null);
        due.disable();
      }
    });
  }

  ngOnInit(): void {
    this.loadReferenceData();
  }

  loadReferenceData(): void {
    this.referenceLoading.set(true);
    this.referenceError.set(false);
    Promise.all([firstValueFrom(this.memberService.list()), firstValueFrom(this.sessionService.list())])
      .then(([members, sessions]) => {
        this.members.set(
          members.filter((m) => m.active).sort((a, b) => a.fullName.localeCompare(b.fullName, 'fr')),
        );
        this.sessions.set(sessions);
        this.referenceLoading.set(false);
      })
      .catch(() => {
        this.referenceError.set(true);
        this.referenceLoading.set(false);
      });
  }

  // ---------- Navigation ----------
  /** Passe à l'étape suivante si le formulaire de l'étape courante est valide (erreurs affichées sinon). */
  next(form: { invalid: boolean; markAllAsTouched(): void }): void {
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    this.stepper.next();
  }

  onStepChange(index: number): void {
    this.currentIndex.set(index);
    // Remonte en haut de l'étape sur mobile.
    document.querySelector('.shell__content')?.scrollTo({ top: 0, behavior: 'smooth' });
  }

  selectAll(form: typeof this.participantsForm): void {
    form.controls.memberIds.setValue(this.members().map((m) => m.id));
  }

  clearAll(form: typeof this.participantsForm): void {
    form.controls.memberIds.setValue([]);
  }

  // ---------- Récapitulatif ----------
  memberNames(ids: string[] | null): string[] {
    const byId = new Map(this.members().map((m) => [m.id, m.fullName]));
    return (ids ?? []).map((id) => byId.get(id) ?? id);
  }

  selectedSession(): Session | null {
    const id = this.sessionForm.controls.sessionId.value;
    return this.sessions().find((s) => s.id === id) ?? null;
  }

  // ---------- Enregistrement du brouillon ----------
  /**
   * Crée la cotisation (DRAFT) puis ses liens. Relançable : ce qui a déjà été
   * enregistré n'est pas renvoyé (un 409 « déjà existant » compte comme fait).
   */
  async saveDraft(): Promise<void> {
    this.saving.set(true);
    this.saveError.set(null);
    let stage = 'la cotisation';

    try {
      let definition = this.created();
      if (!definition) {
        const mode = this.amountForm.controls.amountMode.value!;
        definition = await firstValueFrom(
          this.contributionService.create({
            name: this.infoForm.controls.name.value!.trim(),
            description: this.infoForm.controls.description.value?.trim() || null,
            amount: mode === 'FIXED' ? Number(this.amountForm.controls.amount.value) : null,
            amountMode: mode,
            frequency: 'MONTHLY',
            mandatory: this.natureForm.controls.mandatory.value!,
            visibility: this.visibilityForm.controls.visibility.value!,
            fundDestination: this.destinationForm.controls.fundDestination.value!,
          }),
        );
        this.created.set(definition);
      }
      const id = definition.id;

      stage = 'les participants';
      for (const memberId of this.participantsForm.controls.memberIds.value ?? []) {
        if (!this.doneParticipants.has(memberId)) {
          await this.ignoreConflict(firstValueFrom(this.contributionService.addParticipant(id, memberId)));
          this.doneParticipants.add(memberId);
        }
      }

      stage = 'les bénéficiaires';
      for (const memberId of this.beneficiariesForm.controls.memberIds.value ?? []) {
        if (!this.doneBeneficiaries.has(memberId)) {
          await this.ignoreConflict(firstValueFrom(this.contributionService.addBeneficiary(id, memberId)));
          this.doneBeneficiaries.add(memberId);
        }
      }

      stage = 'le rattachement à la séance';
      const sessionId = this.sessionForm.controls.sessionId.value;
      if (sessionId && !this.periodDone) {
        const due = this.sessionForm.controls.dueDate.value;
        await this.ignoreConflict(
          firstValueFrom(
            this.contributionService.addPeriod(id, { sessionId, dueDate: due ? toIsoDate(due) : null }),
          ),
        );
        this.periodDone = true;
      }

      this.saving.set(false);
      this.stepper.next();
    } catch (err) {
      this.saving.set(false);
      const status = err instanceof HttpErrorResponse ? err.status : 0;
      const reason =
        status === 403
          ? "vous n'avez pas les droits nécessaires"
          : status === 404
            ? 'un membre ou la séance choisie est introuvable'
            : status === 400
              ? 'des informations sont invalides'
              : 'le serveur ne répond pas';
      this.saveError.set(
        this.created()
          ? `Le brouillon a été créé, mais l'enregistrement de ${stage} a échoué (${reason}). Réessayez pour compléter.`
          : `La cotisation n'a pas pu être créée (${reason}).`,
      );
    }
  }

  private async ignoreConflict<T>(promise: Promise<T>): Promise<void> {
    try {
      await promise;
    } catch (err) {
      if (!(err instanceof HttpErrorResponse && err.status === 409)) {
        throw err;
      }
    }
  }

  // ---------- Activation ----------
  activate(): void {
    const definition = this.created();
    if (!definition) {
      return;
    }
    const data: ConfirmDialogData = {
      title: 'Activer la cotisation ?',
      message: `« ${definition.name} » sera publiée : ses participants pourront la voir et déclarer leurs paiements.`,
      confirmLabel: 'Activer',
      icon: 'rocket_launch',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '440px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.activating.set(true);
        this.activationError.set(null);
        this.contributionService.activate(definition.id).subscribe({
          next: (updated) => {
            this.created.set(updated);
            this.activating.set(false);
            this.activated.set(true);
          },
          error: () => {
            this.activating.set(false);
            this.activationError.set(
              "L'activation a échoué. La cotisation reste en brouillon ; vous pourrez l'activer depuis la liste.",
            );
          },
        });
      });
  }
}
