import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { fromIsoDate, toIsoDate } from '../../../shared/utils/format';
import { Member } from '../../members/member.model';
import { MemberService } from '../../members/member.service';
import { Session, SessionRequest } from '../session.model';
import { SessionService } from '../session.service';

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
  private readonly dialogRef = inject<MatDialogRef<SessionFormDialog, Session>>(MatDialogRef);
  private readonly data = inject<SessionFormDialogData | null>(MAT_DIALOG_DATA, { optional: true });

  /** Séance modifiée, ou null en création. */
  readonly editing = this.data?.session ?? null;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly membersLoading = signal(true);
  readonly membersError = signal(false);
  readonly members = signal<Member[]>([]);

  readonly form = this.fb.group({
    label: ['', [Validators.required, Validators.maxLength(100)]],
    date: [null as Date | null, Validators.required],
    location: ['', [Validators.required, Validators.maxLength(150)]],
    hostMemberId: [null as string | null, Validators.required],
    beneficiaryMemberIds: [[] as string[], Validators.required],
  });

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
    };
    const save$ = this.editing
      ? this.sessionService.update(this.editing.id, request)
      : this.sessionService.create(request);

    save$.subscribe({
      next: (session) => this.dialogRef.close(session),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.errorMessage.set(
          error.status === 400
            ? (error.error?.message ?? 'Les informations saisies sont invalides.')
            : error.status === 403
              ? `Vous n'avez pas les droits pour ${this.editing ? 'modifier' : 'créer'} une séance.`
              : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
        );
      },
    });
  }
}
