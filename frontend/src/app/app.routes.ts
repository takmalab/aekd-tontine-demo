import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'sessions',
        loadComponent: () =>
          import('./features/sessions/sessions-list/sessions-list').then((m) => m.SessionsList),
      },
      {
        path: 'sessions/:id',
        loadComponent: () =>
          import('./features/sessions/session-detail/session-detail').then((m) => m.SessionDetail),
      },
      {
        path: 'contributions',
        loadComponent: () =>
          import('./features/contributions/contributions-list/contributions-list').then(
            (m) => m.ContributionsList,
          ),
      },
      {
        path: 'contributions/new',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'TRESORIER'] },
        loadComponent: () =>
          import('./features/contributions/contribution-wizard/contribution-wizard').then(
            (m) => m.ContributionWizard,
          ),
      },
      {
        path: 'my-contributions',
        loadComponent: () =>
          import('./features/payments/my-contributions/my-contributions').then((m) => m.MyContributions),
      },
      {
        path: 'payments',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'TRESORIER'] },
        loadComponent: () =>
          import('./features/payments/pending-payments/pending-payments').then((m) => m.PendingPayments),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
