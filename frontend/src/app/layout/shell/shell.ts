import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { catchError, fromEvent, map, merge, of, switchMap, timer } from 'rxjs';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { RoleName } from '../../core/models/auth.model';
import { AppNotification, NOTIFICATION_STYLE } from '../../core/models/notification.model';
import { MemberService } from '../../features/members/member.service';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  roles?: RoleName[];
  /**
   * Espace personnel du membre : affiché pour tout compte ayant un profil membre,
   * quel que soit son rôle (ex. un trésorier qui cotise aussi à la tontine).
   */
  memberSpace?: true;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Tableau de bord', path: '/dashboard', icon: 'dashboard' },
  { label: 'Séances', path: '/sessions', icon: 'event' },
  { label: 'Tournois', path: '/tournaments', icon: 'event_repeat', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Cotisations', path: '/contributions', icon: 'payments' },
  { label: 'Mes cotisations', path: '/my-contributions', icon: 'receipt_long', memberSpace: true },
  { label: 'Paiements', path: '/payments', icon: 'fact_check', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Fonds', path: '/funds', icon: 'account_balance_wallet', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Membres', path: '/members', icon: 'group', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Sanctions', path: '/sanctions', icon: 'gavel' },
  { label: 'Mon épargne', path: '/my-savings', icon: 'savings', memberSpace: true },
  { label: 'Épargnes', path: '/savings', icon: 'savings', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Mes prêts', path: '/my-loans', icon: 'account_balance', memberSpace: true },
  { label: 'Prêts', path: '/loans', icon: 'account_balance', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Politiques de prêt', path: '/loan-policies', icon: 'policy' },
  { label: 'Utilisateurs', path: '/users', icon: 'manage_accounts', roles: ['ADMIN'] },
  { label: "Journal d'audit", path: '/audit', icon: 'history', roles: ['ADMIN'] },
  { label: 'Paramètres SMS', path: '/settings/sms', icon: 'sms', roles: ['ADMIN'] },
];

const ROLE_LABELS: Record<RoleName, string> = {
  ADMIN: 'Administrateur',
  TRESORIER: 'Trésorier',
  MEMBRE: 'Membre',
};

/** Below this width the sidenav becomes an overlay drawer (phones + tablets). */
const COMPACT_QUERY = '(max-width: 1023.98px)';

@Component({
  selector: 'app-shell',
  imports: [
    DatePipe,
    MatBadgeModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly memberService = inject(MemberService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly currentUser = this.authService.currentUser;

  readonly styles = NOTIFICATION_STYLE;
  readonly unreadCount = this.notifications.unreadCount;
  readonly recent = signal<AppNotification[]>([]);
  readonly recentLoading = signal(false);

  /** Le compte connecté a-t-il un profil membre ? (404 sinon, ex. administrateur). */
  private readonly hasMemberProfile = toSignal(
    this.memberService.mine().pipe(
      map(() => true),
      catchError(() => of(false)),
    ),
    { initialValue: this.authService.hasRole('MEMBRE') },
  );

  readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) =>
      item.memberSpace
        ? this.hasMemberProfile() || this.authService.hasRole('MEMBRE')
        : !item.roles || this.authService.hasAnyRole(item.roles),
    ),
  );

  readonly roleLabel = computed(() =>
    (this.currentUser()?.roles ?? []).map((role) => ROLE_LABELS[role] ?? role).join(' · '),
  );

  readonly initials = computed(() => {
    const email = this.currentUser()?.email ?? '';
    return email.slice(0, 2).toUpperCase();
  });

  /** True on phones and tablets: sidenav is an overlay drawer toggled from the topbar. */
  readonly isCompact = toSignal(
    this.breakpointObserver.observe(COMPACT_QUERY).pipe(map((state) => state.matches)),
    { initialValue: this.breakpointObserver.isMatched(COMPACT_QUERY) },
  );

  /** Open state of the drawer in compact mode (ignored on desktop, where it is always open). */
  readonly drawerOpen = signal(false);

  readonly sidenavOpened = computed(() => !this.isCompact() || this.drawerOpen());

  constructor() {
    // Reset the drawer whenever the layout switches between compact and desktop.
    effect(() => {
      this.isCompact();
      this.drawerOpen.set(false);
    });

    // Compteur de la cloche : toutes les 60 s, et dès que l'onglet redevient visible.
    merge(timer(0, 60_000), fromEvent(document, 'visibilitychange'))
      .pipe(
        switchMap(() => (document.visibilityState === 'visible' ? this.notifications.refreshUnread() : of(0))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  loadRecent(): void {
    this.recentLoading.set(this.recent().length === 0);
    this.notifications.list(8).subscribe({
      next: (list) => {
        this.recent.set(list);
        this.recentLoading.set(false);
        this.notifications.refreshUnread().subscribe();
      },
      error: () => this.recentLoading.set(false),
    });
  }

  openNotification(notification: AppNotification): void {
    this.notifications.open(notification);
    this.recent.update((list) => list.map((n) => (n.id === notification.id ? { ...n, read: true } : n)));
    this.onNavigate();
  }

  markAllRead(): void {
    this.notifications.markAllRead().subscribe(() =>
      this.recent.update((list) => list.map((n) => ({ ...n, read: true }))),
    );
  }

  toggleDrawer(): void {
    this.drawerOpen.update((open) => !open);
  }

  onNavigate(): void {
    if (this.isCompact()) {
      this.drawerOpen.set(false);
    }
  }

  onSidenavClosed(): void {
    this.drawerOpen.set(false);
  }

  /**
   * Le push de cet appareil est désactivé à la déconnexion : sur un appareil
   * partagé, les notifications d'un membre ne doivent pas arriver chez un autre.
   */
  logout(): void {
    this.notifications
      .disablePush()
      .catch(() => undefined)
      .finally(() => {
        this.authService.logout();
        this.notifications.reset();
        this.router.navigate(['/login']);
      });
  }
}
