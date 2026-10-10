import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { NotificationSoundService } from '../../../core/services/notification-sound.service';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { AppNotification, NOTIFICATION_STYLE, PushState } from '../../../core/models/notification.model';

/** Notifications du compte, push sur cet appareil et rappels (docs/decisions.md n° 44). */
@Component({
  selector: 'app-notifications-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
  ],
  templateUrl: './notifications-page.html',
  styleUrl: './notifications-page.scss',
})
export class NotificationsPage implements OnInit {
  private readonly notifications = inject(NotificationService);
  private readonly snackBar = inject(MatSnackBar);
  readonly isStaff = inject(AuthService).hasAnyRole(['ADMIN', 'TRESORIER']);

  readonly styles = NOTIFICATION_STYLE;
  readonly unreadCount = this.notifications.unreadCount;
  readonly pushState = this.notifications.pushState;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly items = signal<AppNotification[]>([]);
  readonly filter = signal<'all' | 'unread'>('all');
  readonly visible = computed(() =>
    this.filter() === 'unread' ? this.items().filter((n) => !n.read) : this.items(),
  );

  readonly pushBusy = signal(false);

  private readonly sound = inject(NotificationSoundService);
  readonly soundEnabled = this.sound.enabled;

  readonly reminderDays = new FormControl<number | null>(null, [Validators.min(0), Validators.max(60)]);
  readonly settingsSaving = signal(false);
  readonly savedReminderDays = signal<number | null>(null);

  ngOnInit(): void {
    this.load();
    this.notifications.refreshPushState();
    if (this.isStaff) {
      this.notifications.settings().subscribe((s) => {
        this.reminderDays.setValue(s.reminderDaysBefore);
        this.savedReminderDays.set(s.reminderDaysBefore);
      });
    }
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.notifications.list(100).subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
        this.notifications.refreshUnread().subscribe();
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  open(notification: AppNotification): void {
    this.items.update((list) => list.map((n) => (n.id === notification.id ? { ...n, read: true } : n)));
    this.notifications.open(notification);
  }

  markAllRead(): void {
    this.notifications.markAllRead().subscribe(() =>
      this.items.update((list) => list.map((n) => ({ ...n, read: true }))),
    );
  }

  async enablePush(): Promise<void> {
    await this.runPush(async () => {
      const state = await this.notifications.enablePush();
      this.snackBar.open(this.pushMessage(state), 'OK', { duration: 5000 });
    });
  }

  async disablePush(): Promise<void> {
    await this.runPush(async () => {
      await this.notifications.disablePush();
      this.snackBar.open('Notifications push désactivées sur cet appareil', 'OK', { duration: 4000 });
    });
  }

  setSound(enabled: boolean): void {
    this.sound.setEnabled(enabled);
    if (enabled) {
      this.sound.play();
    }
  }

  playSound(): void {
    this.sound.play();
  }

  sendTest(): void {
    this.notifications.sendTest().subscribe({
      next: () => {
        this.snackBar.open('Notification de test envoyée', 'OK', { duration: 4000 });
        this.load();
      },
      error: () => this.snackBar.open("L'envoi du test a échoué.", 'OK', { duration: 5000 }),
    });
  }

  saveSettings(): void {
    if (this.reminderDays.invalid) {
      return;
    }
    const value = this.reminderDays.value;
    this.settingsSaving.set(true);
    this.notifications
      .updateSettings({ reminderDaysBefore: value === null || (value as unknown) === '' ? null : Number(value) })
      .subscribe({
        next: (s) => {
          this.settingsSaving.set(false);
          this.savedReminderDays.set(s.reminderDaysBefore);
          this.snackBar.open(
            s.reminderDaysBefore === null
              ? 'Rappels désactivés'
              : `Rappels envoyés ${s.reminderDaysBefore} jour(s) avant la date limite`,
            'OK',
            { duration: 4000 },
          );
        },
        error: () => {
          this.settingsSaving.set(false);
          this.snackBar.open("L'enregistrement a échoué.", 'OK', { duration: 5000 });
        },
      });
  }

  private async runPush(action: () => Promise<void>): Promise<void> {
    this.pushBusy.set(true);
    try {
      await action();
    } catch (error) {
      console.error('[AEKD] Notifications push', error);
      this.snackBar.open(
        `Impossible de modifier les notifications push sur cet appareil : ${this.describe(error)}`,
        'OK',
        { duration: 10000 },
      );
      await this.notifications.refreshPushState();
    } finally {
      this.pushBusy.set(false);
    }
  }

  /** Cause lisible : serveur injoignable ou ancien, ou refus du navigateur / du service push. */
  private describe(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return 'le serveur ne répond pas.';
      }
      if (error.status === 404) {
        return "le serveur ne connaît pas encore les notifications (backend à recompiler et relancer).";
      }
      return `erreur du serveur (${error.status}).`;
    }
    if (error instanceof DOMException || error instanceof Error) {
      return `${error.name} – ${error.message}`;
    }
    return 'erreur inconnue.';
  }

  private pushMessage(state: PushState): string {
    switch (state) {
      case 'on':
        return 'Notifications push activées sur cet appareil';
      case 'denied':
        return 'Notifications bloquées : autorisez-les dans les réglages du navigateur.';
      case 'unsupported':
        return 'Ce navigateur ne prend pas en charge les notifications push.';
      default:
        return 'Notifications push non activées.';
    }
  }
}
