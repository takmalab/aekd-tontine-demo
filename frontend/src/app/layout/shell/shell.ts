import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { catchError, map, of } from 'rxjs';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../core/services/auth.service';
import { RoleName } from '../../core/models/auth.model';
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
  { label: 'Cotisations', path: '/contributions', icon: 'payments' },
  { label: 'Mes cotisations', path: '/my-contributions', icon: 'receipt_long', memberSpace: true },
  { label: 'Paiements', path: '/payments', icon: 'fact_check', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Membres', path: '/members', icon: 'group', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Sanctions', path: '/sanctions', icon: 'gavel' },
  { label: 'Mes prêts', path: '/my-loans', icon: 'account_balance', memberSpace: true },
  { label: 'Prêts', path: '/loans', icon: 'account_balance', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Politiques de prêt', path: '/loan-policies', icon: 'policy' },
  { label: 'Utilisateurs', path: '/users', icon: 'manage_accounts', roles: ['ADMIN'] },
  { label: "Journal d'audit", path: '/audit', icon: 'history', roles: ['ADMIN'] },
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

  readonly currentUser = this.authService.currentUser;

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

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
