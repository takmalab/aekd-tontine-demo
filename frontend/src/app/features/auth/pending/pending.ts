import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

/**
 * Compte inscrit, pas encore validé (docs/decisions.md n° 54) : on y arrive après
 * l'inscription, ou en se connectant tant que l'administrateur n'a pas validé.
 * `history.state.name` : prénom affiché, transmis par l'inscription si connu.
 */
@Component({
  selector: 'app-pending',
  imports: [RouterLink, MatCardModule, MatButtonModule, MatIconModule],
  template: `
    <div class="register-page aekd-pattern-symboles">
      <div class="register-page__glow register-page__glow--orange"></div>
      <div class="register-page__glow register-page__glow--brown"></div>

      <mat-card class="register-card" appearance="outlined">
        <div class="register-card__header">
          <img class="register-card__logo" src="assets/logo-aekd.png" alt="Amicale des Enfants Kamkop de Douala – AEKD" />
          <span class="pending__icon"><mat-icon>hourglass_top</mat-icon></span>
          <h1 class="register-card__title">Compte en attente de validation</h1>
          <p class="register-card__subtitle">
            @if (name) {
              Merci {{ name }} !
            }
            Votre compte a bien été créé. Un administrateur doit le valider avant que vous puissiez accéder à
            l'application.
          </p>
        </div>

        <mat-card-content>
          <ul class="pending__steps">
            <li><mat-icon>check_circle</mat-icon> Inscription enregistrée</li>
            <li class="pending__steps--current"><mat-icon>hourglass_top</mat-icon> Validation par l'administrateur</li>
            <li><mat-icon>login</mat-icon> Connexion à l'application</li>
          </ul>
          <p class="pending__hint">
            Revenez vous connecter un peu plus tard : dès que votre compte est validé, vous accédez directement à
            votre espace.
          </p>
          <a mat-flat-button routerLink="/login" class="register-form__submit">Retour à la connexion</a>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styleUrls: ['../register/register.scss', './pending.scss'],
})
export class Pending {
  readonly name: string | null = (history.state?.name as string | undefined)?.split(' ')[0] ?? null;
}
