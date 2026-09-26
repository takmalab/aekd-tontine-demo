import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Member } from '../member.model';
import { MemberService } from '../member.service';

/**
 * Édition d'un membre (CLAUDE.md §6) — réservée à l'ADMIN côté backend
 * (`MemberController.update`). N'expose que les champs acceptés par
 * `MemberUpdateRequest` : nom, téléphone, actif/inactif.
 */
@Component({
  selector: 'app-member-edit-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
  ],
  templateUrl: './member-edit-dialog.html',
  styleUrl: './member-edit-dialog.scss',
})
export class MemberEditDialog {
  private readonly fb = inject(FormBuilder);
  private readonly memberService = inject(MemberService);
  private readonly dialogRef = inject<MatDialogRef<MemberEditDialog, Member>>(MatDialogRef);
  readonly member = inject<Member>(MAT_DIALOG_DATA);

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    fullName: [this.member.fullName, [Validators.required, Validators.maxLength(150)]],
    phone: [this.member.phone ?? ''],
    active: [this.member.active],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.memberService
      .update(this.member.id, {
        fullName: v.fullName!.trim(),
        phone: v.phone?.trim() || null,
        active: v.active!,
      })
      .subscribe({
        next: (member) => this.dialogRef.close(member),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 403
              ? "Action réservée à l'administrateur."
              : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
