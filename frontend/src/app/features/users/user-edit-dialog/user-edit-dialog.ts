import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { AuthService } from '../../../core/services/auth.service';
import { RoleName } from '../../../core/models/auth.model';
import { AppUser, ROLE_LABELS, USER_STATUS_LABELS, UserStatus } from '../user.model';
import { UserService } from '../user.service';

/** Rôles administrateurs : attribués par un super administrateur seulement (decisions.md n° 56). */
const ALL_ROLES: RoleName[] = ['SUPER_ADMIN', 'ADMIN', 'TRESORIER', 'MEMBRE'];
const STAFF_ROLES: RoleName[] = ['TRESORIER', 'MEMBRE'];
const ALL_STATUSES: UserStatus[] = ['PENDING_VALIDATION', 'ACTIVE', 'DISABLED'];

/**
 * Édition des rôles, du statut et du numéro de connexion par code SMS
 * d'un compte existant (CLAUDE.md §6, ADMIN uniquement). Le numéro est
 * recopié sur le profil membre ; le reste du profil est géré séparément
 * (écran Membres).
 */
@Component({
  selector: 'app-user-edit-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
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

  readonly allRoles = inject(AuthService).hasRole('SUPER_ADMIN') ? ALL_ROLES : STAFF_ROLES;
  readonly allStatuses = ALL_STATUSES;
  readonly roleLabels = ROLE_LABELS;
  readonly statusLabels = USER_STATUS_LABELS;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    roles: [[...this.user.roles], [Validators.required]],
    status: [this.user.status, [Validators.required]],
    phone: [this.user.phone ?? ''],
  });

  submit(): void {
    if (this.form.invalid || (this.form.controls.roles.value ?? []).length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.userService
      .update(this.user.id, { roles: v.roles!, status: v.status!, phone: v.phone?.trim() ?? '' })
      .subscribe({
      next: (user) => this.dialogRef.close(user),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        const message = error.error?.message;
        this.errorMessage.set(
          (error.status === 400 || error.status === 409) && message
            ? message
            : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
        );
      },
    });
  }
}
