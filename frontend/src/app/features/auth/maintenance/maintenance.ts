import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

/**
 * Page de maintenance (docs/decisions.md n° 57 et 70) : un membre qui se connecte, ou dont la
 * session est coupée, pendant la maintenance arrive ici au lieu d'un message d'erreur. Le message
 * est celui saisi par le super administrateur. « Réessayer » revient à la connexion dès que la
 * maintenance est terminée ; le super administrateur garde un accès discret à la connexion.
 */
@Component({
  selector: 'app-maintenance',
  imports: [RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './maintenance.html',
  styleUrls: ['../register/register.scss', './maintenance.scss'],
})
export class Maintenance implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  /** Message transmis par la page précédente, en attendant celui du serveur. */
  readonly message = signal<string | null>((history.state as { message?: string } | null)?.message ?? null);
  readonly checking = signal(false);
  readonly stillInMaintenance = signal(false);

  ngOnInit(): void {
    this.check(false);
  }

  /** Relit l'état public ; si la maintenance est terminée, retour à la connexion. */
  check(userAction = true): void {
    this.checking.set(true);
    this.authService.publicInfo().subscribe({
      next: (info) => {
        this.checking.set(false);
        if (!info.maintenance) {
          this.router.navigate(['/login']);
          return;
        }
        if (info.maintenanceMessage) {
          this.message.set(info.maintenanceMessage);
        }
        this.stillInMaintenance.set(userAction);
      },
      error: () => {
        this.checking.set(false);
        this.stillInMaintenance.set(userAction);
      },
    });
  }
}
