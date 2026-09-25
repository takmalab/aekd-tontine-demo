import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../../core/services/auth.service';
import { RoleName } from '../../core/models/auth.model';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  roles?: RoleName[];
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Tableau de bord', path: '/dashboard', icon: 'dashboard' },
  { label: 'Séances', path: '/sessions', icon: 'event' },
  { label: 'Cotisations', path: '/contributions', icon: 'payments' },
  { label: 'Membres', path: '/members', icon: 'group', roles: ['ADMIN', 'TRESORIER'] },
  { label: 'Sanctions', path: '/sanctions', icon: 'gavel' },
  { label: 'Prêts', path: '/loans', icon: 'account_balance' },
  { label: 'Utilisateurs', path: '/users', icon: 'manage_accounts', roles: ['ADMIN'] },
  { label: "Journal d'audit", path: '/audit', icon: 'history', roles: ['ADMIN'] },
];

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

  readonly currentUser = this.authService.currentUser;

  readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) => !item.roles || this.authService.hasAnyRole(item.roles)),
  );

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
