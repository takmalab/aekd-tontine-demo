import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RoleName } from '../../../core/models/auth.model';
import { AppUser, ROLE_LABELS, USER_STATUS_LABELS, UserStatus } from '../user.model';
import { UserService } from '../user.service';

const ALL_ROLES: RoleName[] = ['ADMIN', 'TRESORIER', 'MEMBRE'];
const ALL_STATUSES: UserStatus[] = ['PENDING_VALIDATION', 'ACTIVE', 'DISABLED'];

/**
 * Édition des rôles et du statut d'un compte existant (CLAUDE.md §6,
 * ADMIN uniquement). Ne touche pas au profil membre associé, géré
 * séparément (écran Membres).
 */
@Component({
  selector: 'app-user-edit-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './user-edit-dialog.html',
  styleUrl: './user-edit-dialog.scss',
})
export class UserEditDialog {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly dialogRef = inject<MatDialogRef<UserEditDialog, AppUser>>(MatDialogRef);
  readonly user = inject<AppUser>(MAT_DIALOG_DATA);

  readonly allRoles = ALL_ROLES;
  readonly allStatuses = ALL_STATUSES;
  readonly roleLabels = ROLE_LABELS;
  readonly statusLabels = USER_STATUS_LABELS;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    roles: [[...this.user.roles], [Validators.required]],
    status: [this.user.status, [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid || (this.form.controls.roles.value ?? []).length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.userService.update(this.user.id, { roles: v.roles!, status: v.status! }).subscribe({
      next: (user) => this.dialogRef.close(user),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.errorMessage.set(
          error.status === 400
            ? 'Un utilisateur doit avoir au moins un rôle.'
            : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
        );
      },
    });
  }
}
