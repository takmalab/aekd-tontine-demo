import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Sur un 401, le jeton d'accès (60 min) a expiré : la session est renouvelée
 * en silence avec le jeton de renouvellement de l'appareil, puis la requête
 * est rejouée (docs/decisions.md n° 54). Si le renouvellement échoue (session
 * de plus de 30 jours sans activité, déconnexion, compte désactivé), on
 * nettoie et on renvoie vers /login. L'erreur continue de se propager pour que
 * le composant appelant puisse afficher son propre message.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Maintenance (decisions.md n° 57 et 70) : session coupée, page de maintenance.
      if (error.status === 503 && error.error?.maintenance) {
        authService.clearSession();
        router.navigate(['/maintenance'], { state: { message: error.error?.message } });
        return throwError(() => error);
      }
      // Les appels d'authentification gèrent eux-mêmes leurs erreurs (pas de boucle).
      if (error.status !== 401 || req.url.includes('/auth/')) {
        return throwError(() => error);
      }
      if (!authService.hasRefreshToken()) {
        authService.clearSession();
        router.navigate(['/login']);
        return throwError(() => error);
      }
      return authService.refreshSession().pipe(
        switchMap((session) => next(req.clone({ setHeaders: { Authorization: `Bearer ${session.token}` } }))),
        catchError((refreshError: HttpErrorResponse) => {
          // Session refusée par le serveur. Une coupure réseau (fréquente sur mobile) ne déconnecte pas.
          if (refreshError.status === 401 || refreshError.status === 403) {
            authService.clearSession();
            router.navigate(['/login']);
          } else if (refreshError.status === 503) {
            // Renouvellement refusé pendant la maintenance (décision n° 70).
            authService.clearSession();
            router.navigate(['/maintenance'], { state: { message: refreshError.error?.message } });
          }
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
