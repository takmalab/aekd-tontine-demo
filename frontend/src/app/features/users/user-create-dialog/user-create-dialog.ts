import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RoleName } from '../../../core/models/auth.model';
import { toIsoDate } from '../../../shared/utils/format';
import { AppUser, ROLE_LABELS } from '../user.model';
import { UserService } from '../user.service';

const ALL_ROLES: RoleName[] = ['ADMIN', 'TRESORIER', 'MEMBRE'];

/**
 * Création directe d'un compte par un administrateur (CLAUDE.md §6).
 * Contrairement à l'auto-inscription, le compte est créé ACTIVE et
 * peut recevoir n'importe quel rôle. Le profil membre est facultatif
 * (utile pour créer un ADMIN/TRESORIER sans membre associé).
 */
@Component({
  selector: 'app-user-create-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './user-create-dialog.html',
  styleUrl: './user-create-dialog.scss',
})
export class UserCreateDialog {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly dialogRef = inject<MatDialogRef<UserCreateDialog, AppUser>>(MatDialogRef);

  readonly allRoles = ALL_ROLES;
  readonly roleLabels = ROLE_LABELS;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    roles: [[] as RoleName[], [Validators.required]],
    withMember: [true],
    fullName: [''],
    phone: [''],
    joinDate: [new Date() as Date | null],
  });

  constructor() {
    this.form.controls.withMember.valueChanges.subscribe((withMember) => {
      const fullName = this.form.controls.fullName;
      const joinDate = this.form.controls.joinDate;
      if (withMember) {
        fullName.setValidators([Validators.required, Validators.maxLength(150)]);
        joinDate.setValidators([Validators.required]);
      } else {
        fullName.clearValidators();
        joinDate.clearValidators();
      }
      fullName.updateValueAndValidity();
      joinDate.updateValueAndValidity();
    });
    this.form.controls.fullName.setValidators([Validators.required, Validators.maxLength(150)]);
    this.form.controls.joinDate.setValidators([Validators.required]);
  }

  submit(): void {
    if (this.form.invalid || (this.form.controls.roles.value ?? []).length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);

    this.userService
      .create({
        email: v.email!.trim(),
        password: v.password!,
        roles: v.roles!,
        member: v.withMember
          ? {
              fullName: v.fullName!.trim(),
              phone: v.phone?.trim() || null,
              joinDate: toIsoDate(v.joinDate!),
            }
          : null,
      })
      .subscribe({
        next: (user) => this.dialogRef.close(user),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 409
              ? 'Un compte existe déjà avec cet email.'
              : error.status === 400
                ? 'Informations invalides.'
                : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
