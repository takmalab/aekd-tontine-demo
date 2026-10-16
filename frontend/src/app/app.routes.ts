import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'pending',
    loadComponent: () => import('./features/auth/pending/pending').then((m) => m.Pending),
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
        path: 'tournaments',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'TRESORIER'] },
        loadComponent: () =>
          import('./features/tournaments/tournaments-page/tournaments-page').then((m) => m.TournamentsPage),
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
        path: 'contributions/:id',
        loadComponent: () =>
          import('./features/contributions/contribution-detail/contribution-detail').then(
            (m) => m.ContributionDetail,
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
      {
        path: 'my-savings',
        loadComponent: () => import('./features/savings/my-savings/my-savings').then((m) => m.MySavings),
      },
      {
        path: 'savings',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'TRESORIER'] },
        loadComponent: () =>
          import('./features/savings/savings-admin/savings-admin').then((m) => m.SavingsAdmin),
      },
      {
        path: 'my-loans',
        loadComponent: () => import('./features/loans/my-loans/my-loans').then((m) => m.MyLoans),
      },
      {
        path: 'loans',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'TRESORIER'] },
        loadComponent: () => import('./features/loans/loans-admin/loans-admin').then((m) => m.LoansAdmin),
      },
      {
        path: 'loan-policies',
        loadComponent: () =>
          import('./features/loans/loan-policies/loan-policies').then((m) => m.LoanPolicies),
      },
      {
        path: 'sanctions',
        loadComponent: () => import('./features/sanctions/sanctions-page').then((m) => m.SanctionsPage),
      },
      {
        path: 'members',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'TRESORIER'] },
        loadComponent: () =>
          import('./features/members/members-list/members-list').then((m) => m.MembersList),
      },
      {
        path: 'users',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/users/users-list/users-list').then((m) => m.UsersList),
      },
      {
        path: 'audit',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/audit/audit-log/audit-log').then((m) => m.AuditLogPage),
      },
      {
        path: 'funds',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN', 'TRESORIER'] },
        loadComponent: () => import('./features/cash/funds-page/funds-page').then((m) => m.FundsPage),
      },
      {
        path: 'settings/sms',
        canActivate: [roleGuard],
        data: { roles: ['SUPER_ADMIN'] },
        loadComponent: () =>
          import('./features/settings/sms-settings-page/sms-settings-page').then((m) => m.SmsSettingsPage),
      },
      {
        path: 'settings/system',
        canActivate: [roleGuard],
        data: { roles: ['SUPER_ADMIN'] },
        loadComponent: () =>
          import('./features/settings/system-settings-page/system-settings-page').then((m) => m.SystemSettingsPage),
      },
      {
        path: 'settings/notifications',
        canActivate: [roleGuard],
        data: { roles: ['SUPER_ADMIN'] },
        loadComponent: () =>
          import('./features/settings/notification-channels-page/notification-channels-page').then(
            (m) => m.NotificationChannelsPage,
          ),
      },
      {
        path: 'settings/email',
        canActivate: [roleGuard],
        data: { roles: ['SUPER_ADMIN'] },
        loadComponent: () =>
          import('./features/settings/email-settings-page/email-settings-page').then((m) => m.EmailSettingsPage),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notifications-page/notifications-page').then((m) => m.NotificationsPage),
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/profile/profile-page/profile-page').then((m) => m.ProfilePage),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
