import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { catchError, filter, fromEvent, map, merge, of, startWith, switchMap, timer } from 'rxjs';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
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
import { ProfilePhotoService } from '../../core/services/profile-photo.service';

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

/** Groupe du menu latéral ; sans titre, ses liens s'affichent tout en haut. */
interface NavGroup {
  id: string;
  title?: string;
  icon?: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    id: 'home',
    items: [{ label: 'Tableau de bord', path: '/dashboard', icon: 'dashboard' }],
  },
  {
    id: 'me',
    title: 'Mon espace',
    icon: 'person',
    items: [
      { label: 'Mes cotisations', path: '/my-contributions', icon: 'receipt_long', memberSpace: true },
      { label: 'Mon épargne', path: '/my-savings', icon: 'savings', memberSpace: true },
      { label: 'Mes prêts', path: '/my-loans', icon: 'account_balance', memberSpace: true },
      { label: 'Mon profil', path: '/profile', icon: 'account_circle' },
    ],
  },
  {
    id: 'tontine',
    title: 'Tontine',
    icon: 'diversity_3',
    items: [
      { label: 'Séances', path: '/sessions', icon: 'event' },
      { label: 'Tournois', path: '/tournaments', icon: 'event_repeat', roles: ['ADMIN', 'TRESORIER'] },
      { label: 'Cotisations', path: '/contributions', icon: 'payments' },
      { label: 'Sanctions', path: '/sanctions', icon: 'gavel' },
    ],
  },
  {
    id: 'finances',
    title: 'Finances',
    icon: 'account_balance_wallet',
    items: [
      { label: 'Paiements', path: '/payments', icon: 'fact_check', roles: ['ADMIN', 'TRESORIER'] },
      { label: 'Fonds', path: '/funds', icon: 'account_balance_wallet', roles: ['ADMIN', 'TRESORIER'] },
      { label: 'Épargnes', path: '/savings', icon: 'savings', roles: ['ADMIN', 'TRESORIER'] },
      { label: 'Prêts', path: '/loans', icon: 'account_balance', roles: ['ADMIN', 'TRESORIER'] },
    ],
  },
  {
    id: 'admin',
    title: 'Administration',
    icon: 'admin_panel_settings',
    items: [
      { label: 'Membres', path: '/members', icon: 'group', roles: ['ADMIN', 'TRESORIER'] },
      { label: 'Utilisateurs', path: '/users', icon: 'manage_accounts', roles: ['ADMIN'] },
      { label: "Journal d'audit", path: '/audit', icon: 'history', roles: ['ADMIN'] },
    ],
  },
  {
    id: 'settings',
    title: 'Paramètres',
    icon: 'settings',
    items: [
      { label: 'Politiques de prêt', path: '/loan-policies', icon: 'policy' },
      { label: 'Paramètres système', path: '/settings/system', icon: 'tune', roles: ['SUPER_ADMIN'] },
      { label: 'Paramètres SMS', path: '/settings/sms', icon: 'sms', roles: ['SUPER_ADMIN'] },
      { label: 'Paramètres e-mail', path: '/settings/email', icon: 'mail', roles: ['SUPER_ADMIN'] },
      { label: 'Notifications', path: '/notifications', icon: 'notifications' },
    ],
  },
];

/** Groupes repliés par l'utilisateur, gardés d'une visite à l'autre sur cet appareil. */
const COLLAPSED_KEY = 'aekd.nav.collapsed';

function readCollapsed(): string[] {
  try {
    const value = JSON.parse(localStorage.getItem(COLLAPSED_KEY) ?? '[]');
    return Array.isArray(value) ? value : [];
  } catch {
    return [];
  }
}

const ROLE_LABELS: Record<RoleName, string> = {
  SUPER_ADMIN: 'Super administrateur',
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
  private readonly photoService = inject(ProfilePhotoService);

  readonly photoUrl = this.photoService.photoUrl;

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

  /** Groupes du menu, réduits aux liens permis ; un groupe vide n'est pas affiché. */
  readonly navGroups = computed(() =>
    NAV_GROUPS.map((group) => ({
      ...group,
      items: group.items.filter((item) =>
        item.memberSpace
          ? this.hasMemberProfile() || this.authService.hasRole('MEMBRE')
          : !item.roles || this.authService.hasAnyRole(item.roles),
      ),
    })).filter((group) => group.items.length > 0),
  );

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  readonly collapsed = signal<string[]>(readCollapsed());

  /** Le groupe de la page affichée reste toujours ouvert. */
  isOpen(group: NavGroup): boolean {
    if (!group.title) {
      return true;
    }
    const url = this.url().split('?')[0];
    const active = group.items.some((item) => url === item.path || url.startsWith(item.path + '/'));
    return active || !this.collapsed().includes(group.id);
  }

  toggleGroup(group: NavGroup): void {
    const next = this.collapsed().includes(group.id)
      ? this.collapsed().filter((id) => id !== group.id)
      : [...this.collapsed(), group.id];
    this.collapsed.set(next);
    try {
      localStorage.setItem(COLLAPSED_KEY, JSON.stringify(next));
    } catch {
      // Préférence d'affichage seulement : sans stockage, le menu reste utilisable.
    }
  }

  readonly roleLabel = computed(() =>
    (this.currentUser()?.roles ?? [])
      .filter((role, _, all) => !(role === 'ADMIN' && all.includes('SUPER_ADMIN')))
      .map((role) => ROLE_LABELS[role] ?? role)
      .join(' · '),
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

    this.photoService.load().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();

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
        this.photoService.reset();
        this.router.navigate(['/login']);
      });
  }
}
