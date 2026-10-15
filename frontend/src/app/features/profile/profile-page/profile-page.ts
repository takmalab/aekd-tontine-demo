import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { OtpInput } from '../../../shared/components/otp-input/otp-input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, of } from 'rxjs';
import { RoleName } from '../../../core/models/auth.model';
import { AuthService } from '../../../core/services/auth.service';
import { ProfilePhotoService } from '../../../core/services/profile-photo.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { MemberService } from '../../members/member.service';

const ROLE_LABELS: Record<RoleName, string> = {
  SUPER_ADMIN: 'Super administrateur',
  ADMIN: 'Administrateur',
  TRESORIER: 'Trésorier',
  MEMBRE: 'Membre',
};

/** « Mon profil » : photo de profil et informations du compte connecté. */
@Component({
  selector: 'app-profile-page',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, OtpInput],
  templateUrl: './profile-page.html',
  styleUrl: './profile-page.scss',
})
export class ProfilePage {
  private readonly authService = inject(AuthService);
  private readonly photoService = inject(ProfilePhotoService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly user = this.authService.currentUser;
  readonly photoUrl = this.photoService.photoUrl;
  readonly busy = signal(false);

  /** Profil membre (absent pour un administrateur qui ne cotise pas). */
  readonly member = toSignal(inject(MemberService).mine().pipe(catchError(() => of(null))), { initialValue: null });

  // ---------- Adresse e-mail (decisions.md n° 55) ----------

  /** null tant que l'état n'est pas connu. */
  readonly emailVerified = signal<boolean | null>(null);
  readonly emailStep = signal<'idle' | 'code'>('idle');
  readonly emailBusy = signal(false);
  readonly emailError = signal<string | null>(null);
  readonly emailCode = new FormControl('', [Validators.required, Validators.pattern(/^\d{6}$/)]);

  constructor() {
    this.authService.profileEmail().subscribe({
      next: (status) => this.emailVerified.set(status.verified),
      error: () => this.emailVerified.set(null),
    });
  }

  sendEmailCode(): void {
    this.emailBusy.set(true);
    this.emailError.set(null);
    this.authService.sendProfileEmailCode().subscribe({
      next: () => {
        this.emailBusy.set(false);
        this.emailStep.set('code');
        this.emailCode.reset();
      },
      error: (error: HttpErrorResponse) => {
        this.emailBusy.set(false);
        this.emailError.set(error.error?.message || "L'envoi du code a échoué.");
      },
    });
  }

  verifyEmailCode(): void {
    if (this.emailBusy() || this.emailCode.invalid) {
      return;
    }
    this.emailBusy.set(true);
    this.emailError.set(null);
    this.authService.verifyProfileEmail(this.emailCode.value!).subscribe({
      next: () => {
        this.emailBusy.set(false);
        this.emailVerified.set(true);
        this.emailStep.set('idle');
        this.snackBar.open('Adresse e-mail vérifiée', 'OK', { duration: 4000 });
      },
      error: (error: HttpErrorResponse) => {
        this.emailBusy.set(false);
        this.emailError.set(error.error?.message || 'Code invalide ou expiré.');
        this.emailCode.reset();
      },
    });
  }

  readonly roles = computed(() => (this.user()?.roles ?? []).map((r) => ROLE_LABELS[r] ?? r).join(' · '));
  readonly initials = computed(() => {
    const name = this.member()?.fullName ?? this.user()?.email ?? '';
    const parts = name.split(/[\s@.]+/).filter(Boolean);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
  });

  pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.snackBar.open('Choisissez une image (JPG, PNG…).', 'OK', { duration: 5000 });
      return;
    }
    this.busy.set(true);
    this.photoService.upload(file).subscribe({
      next: () => {
        this.busy.set(false);
        this.snackBar.open('Photo de profil mise à jour', 'OK', { duration: 4000 });
      },
      error: () => {
        this.busy.set(false);
        this.snackBar.open("La photo n'a pas pu être enregistrée.", 'OK', { duration: 6000 });
      },
    });
  }

  remove(): void {
    const data: ConfirmDialogData = {
      title: 'Retirer la photo ?',
      message: 'Vos initiales seront affichées à la place.',
      confirmLabel: 'Retirer',
      cancelLabel: 'Retour',
      icon: 'hide_image',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '420px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busy.set(true);
        this.photoService.remove().subscribe({
          next: () => {
            this.busy.set(false);
            this.snackBar.open('Photo retirée', 'OK', { duration: 4000 });
          },
          error: () => {
            this.busy.set(false);
            this.snackBar.open("La photo n'a pas pu être retirée.", 'OK', { duration: 6000 });
          },
        });
      });
  }
}
