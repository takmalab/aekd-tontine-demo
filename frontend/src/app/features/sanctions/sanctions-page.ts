import { Component, inject, input } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { MySanctions } from './my-sanctions/my-sanctions';
import { StaffSanctions } from './staff-sanctions/staff-sanctions';

/**
 * Entrée « Sanctions » du menu : vue de gestion pour ADMIN et TRESORIER,
 * « Mes sanctions » pour un membre (même logique que le tableau de bord).
 */
@Component({
  selector: 'app-sanctions-page',
  imports: [MySanctions, StaffSanctions],
  template: `
    @if (isStaff) {
      <app-staff-sanctions [contribution]="contribution()" />
    } @else {
      <app-my-sanctions />
    }
  `,
})
export class SanctionsPage {
  private readonly authService = inject(AuthService);
  readonly isStaff = this.authService.hasAnyRole(['ADMIN', 'TRESORIER']);
  /** `?contribution=<id>` : cotisation présélectionnée (vue de gestion). */
  readonly contribution = input<string | undefined>();
}
