import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, finalize, shareReplay, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CurrentUser,
  LoginRequest,
  LoginResponse,
  OtpRequest,
  OtpRequestResponse,
  OtpVerifyRequest,
  RegisterRequest,
  RegisterResponse,
  RoleName,
} from '../models/auth.model';

const TOKEN_KEY = 'aekd_token';
/** Jeton de renouvellement de la session sur cet appareil (decisions.md n° 54). */
const REFRESH_KEY = 'aekd_refresh';

/**
 * Hiérarchie des rôles, miroir de SecurityConfig.roleHierarchy (backend) :
 * l'ADMIN a tous les droits du TRESORIER (docs/decisions.md n° 40). Il ne
 * devient pas MEMBRE : l'espace personnel dépend du profil membre.
 */
const IMPLIED_ROLES: Partial<Record<RoleName, RoleName[]>> = {
  ADMIN: ['TRESORIER'],
};

function effectiveRoles(roles: RoleName[]): Set<RoleName> {
  return new Set(roles.flatMap((role) => [role, ...(IMPLIED_ROLES[role] ?? [])]));
}
const USER_KEY = 'aekd_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUserSignal = signal<CurrentUser | null>(this.readStoredUser());

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);

  /** Renouvellement en cours, partagé : plusieurs requêtes expirées n'en déclenchent qu'un. */
  private refreshing: Observable<LoginResponse> | null = null;

  constructor(private readonly http: HttpClient) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, request)
      .pipe(tap((response) => this.setSession(response)));
  }

  /** Connexion par OTP, étape 1 : envoi d'un code par SMS. */
  requestOtp(request: OtpRequest): Observable<OtpRequestResponse> {
    return this.http.post<OtpRequestResponse>(`${environment.apiUrl}/auth/otp/request`, request);
  }

  /** Connexion par OTP, étape 2 : même session qu'une connexion par email. */
  verifyOtp(request: OtpVerifyRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/otp/verify`, request)
      .pipe(tap((response) => this.setSession(response)));
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${environment.apiUrl}/auth/register`, request);
  }

  /**
   * Déconnexion volontaire : le serveur révoque la session de cet appareil, qui
   * devra se reconnecter (code SMS ou mot de passe).
   */
  logout(): void {
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    if (refreshToken) {
      this.http.post(`${environment.apiUrl}/auth/logout`, { refreshToken }).subscribe({ error: () => undefined });
    }
    this.clearSession();
  }

  /** Oublie la session locale (sans appel au serveur). */
  clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUserSignal.set(null);
  }

  hasRefreshToken(): boolean {
    return !!localStorage.getItem(REFRESH_KEY);
  }

  /**
   * Nouveau jeton d'accès sans ressaisir code ni mot de passe, tant que la session
   * a servi dans les 30 derniers jours (decisions.md n° 54).
   */
  refreshSession(): Observable<LoginResponse> {
    if (!this.refreshing) {
      const refreshToken = localStorage.getItem(REFRESH_KEY) ?? '';
      this.refreshing = this.http
        .post<LoginResponse>(`${environment.apiUrl}/auth/refresh`, { refreshToken })
        .pipe(
          tap((response) => this.setSession(response)),
          finalize(() => (this.refreshing = null)),
          shareReplay(1),
        );
    }
    return this.refreshing;
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  hasRole(role: RoleName): boolean {
    const current = this.currentUserSignal();
    return current ? effectiveRoles(current.roles).has(role) : false;
  }

  hasAnyRole(roles: RoleName[]): boolean {
    const current = this.currentUserSignal();
    if (!current) {
      return false;
    }
    const effective = effectiveRoles(current.roles);
    return roles.some((role) => effective.has(role));
  }

  private setSession(response: LoginResponse): void {
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(REFRESH_KEY, response.refreshToken);
    const user: CurrentUser = { email: response.email, roles: response.roles };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUserSignal.set(user);
  }

  private readStoredUser(): CurrentUser | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as CurrentUser) : null;
    } catch {
      return null;
    }
  }
}
