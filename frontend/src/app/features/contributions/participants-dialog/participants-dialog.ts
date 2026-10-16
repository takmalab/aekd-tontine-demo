import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { firstValueFrom, forkJoin } from 'rxjs';
import { Member } from '../../members/member.model';
import { MemberService } from '../../members/member.service';
import { ContributionDefinition } from '../contribution.model';
import { ContributionService } from '../contribution.service';

export interface ParticipantsDialogData {
  definition: ContributionDefinition;
}

/**
 * Ajout de participants à une cotisation existante (CLAUDE.md §10), par exemple
 * un membre inscrit après la création de la cotisation. Ne propose que les membres
 * actifs, au compte validé, qui ne sont pas encore participants (décision n° 60).
 * Ferme avec le nombre de membres ajoutés.
 */
@Component({
  selector: 'app-participants-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './participants-dialog.html',
  styleUrl: './participants-dialog.scss',
})
export class ParticipantsDialog implements OnInit {
  private readonly contributionService = inject(ContributionService);
  private readonly memberService = inject(MemberService);
  private readonly dialogRef = inject<MatDialogRef<ParticipantsDialog, number>>(MatDialogRef);
  readonly data = inject<ParticipantsDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly participantCount = signal(0);
  /** Membres actifs pas encore participants. */
  readonly candidates = signal<Member[]>([]);
  readonly selected = new FormControl<string[]>([], { nonNullable: true });
  private readonly selectedIds = signal<string[]>([]);
  readonly selectedCount = computed(() => this.selectedIds().length);

  /** Ajouts déjà faits, pour ne pas les renvoyer si l'utilisateur réessaie après une erreur. */
  private readonly added = new Set<string>();

  constructor() {
    this.selected.valueChanges.subscribe((ids) => this.selectedIds.set(ids));
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    forkJoin({
      members: this.memberService.list(),
      participants: this.contributionService.listParticipants(this.data.definition.id),
    }).subscribe({
      next: ({ members, participants }) => {
        const already = new Set(participants.map((p) => p.memberId));
        this.participantCount.set(already.size);
        this.candidates.set(
          members
            .filter((m) => m.active && m.accountStatus !== 'PENDING_VALIDATION' && !already.has(m.id))
            .sort((a, b) => a.fullName.localeCompare(b.fullName, 'fr')),
        );
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  selectAll(): void {
    this.selected.setValue(this.candidates().map((m) => m.id));
  }

  clearAll(): void {
    this.selected.setValue([]);
  }

  async submit(): Promise<void> {
    const ids = this.selected.value.filter((id) => !this.added.has(id));
    if (!ids.length) {
      return;
    }
    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      for (const memberId of ids) {
        try {
          await firstValueFrom(this.contributionService.addParticipant(this.data.definition.id, memberId));
        } catch (err) {
          // 409 : déjà participant (ajouté entre-temps par quelqu'un d'autre) — rien à faire.
          if (!(err instanceof HttpErrorResponse && err.status === 409)) {
            throw err;
          }
        }
        this.added.add(memberId);
      }
      this.dialogRef.close(this.added.size);
    } catch (err) {
      this.saving.set(false);
      const status = err instanceof HttpErrorResponse ? err.status : 0;
      this.errorMessage.set(
        status === 403
          ? "Vous n'avez pas les droits pour modifier les participants."
          : status === 404
            ? 'Membre ou cotisation introuvable.'
            : `L'ajout a échoué (${this.added.size} membre(s) ajouté(s)). Vérifiez que le serveur est démarré, puis réessayez.`,
      );
    }
  }

  cancel(): void {
    // Des ajouts partiels ont pu réussir avant une erreur : la page doit se rafraîchir.
    this.dialogRef.close(this.added.size);
  }
}
