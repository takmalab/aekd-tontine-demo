import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, firstValueFrom, map, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AppNotification, NotificationSettings, PushState } from '../models/notification.model';
import { NotificationSoundService } from './notification-sound.service';

/**
 * Notifications dans l'application (cloche) et Web Push (docs/decisions.md n° 44).
 * Le push passe par un service worker dédié (public/push-sw.js), enregistré avec
 * la portée « /push/ » pour cohabiter avec le service worker Angular.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly baseUrl = `${environment.apiUrl}/notifications`;

  private readonly sound = inject(NotificationSoundService);

  readonly unreadCount = signal(0);
  readonly pushState = signal<PushState>('off');

  /** Faux tant que le premier compteur n'est pas connu : pas de son au chargement de la page. */
  private countKnown = false;

  constructor() {
    if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
      navigator.serviceWorker.addEventListener('message', (event: MessageEvent) => {
        // Clic sur une notification système alors que l'application est ouverte.
        if (event.data?.type === 'aekd-navigate' && typeof event.data.url === 'string') {
          this.router.navigateByUrl(event.data.url);
        }
        // Push reçu : compteur (et son) mis à jour tout de suite, sans attendre le prochain rafraîchissement.
        if (event.data?.type === 'aekd-push') {
          this.refreshUnread().subscribe();
        }
      });
    }
  }

  /** À la déconnexion : le compte suivant repart de zéro, sans son au premier chargement. */
  reset(): void {
    this.unreadCount.set(0);
    this.countKnown = false;
  }

  // ---------- Notifications ----------

  list(limit = 50): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(this.baseUrl, { params: { limit } });
  }

  /**
   * Met à jour le compteur et joue le carillon s'il a augmenté (nouvelle notification).
   * Une erreur (serveur arrêté) laisse la valeur actuelle.
   */
  refreshUnread(): Observable<number> {
    return this.http.get<{ count: number }>(`${this.baseUrl}/unread-count`).pipe(
      map((r) => r.count),
      tap((count) => {
        if (this.countKnown && count > this.unreadCount()) {
          this.sound.play();
        }
        this.countKnown = true;
        this.unreadCount.set(count);
      }),
      catchError(() => of(this.unreadCount())),
    );
  }

  markRead(notification: AppNotification): Observable<AppNotification> {
    if (notification.read) {
      return of(notification);
    }
    return this.http.put<AppNotification>(`${this.baseUrl}/${notification.id}/read`, {}).pipe(
      tap(() => this.unreadCount.update((n) => Math.max(0, n - 1))),
    );
  }

  markAllRead(): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/read-all`, {}).pipe(tap(() => this.unreadCount.set(0)));
  }

  /** Marque comme lue puis ouvre la page liée. */
  open(notification: AppNotification): void {
    this.markRead(notification).subscribe({ error: () => undefined });
    if (notification.link) {
      this.router.navigateByUrl(notification.link);
    }
  }

  // ---------- Réglages (ADMIN, TRESORIER) ----------

  settings(): Observable<NotificationSettings> {
    return this.http.get<NotificationSettings>(`${this.baseUrl}/settings`);
  }

  updateSettings(settings: NotificationSettings): Observable<NotificationSettings> {
    return this.http.put<NotificationSettings>(`${this.baseUrl}/settings`, settings);
  }

  // ---------- Web Push ----------

  get pushSupported(): boolean {
    return (
      typeof window !== 'undefined' &&
      'serviceWorker' in navigator &&
      'PushManager' in window &&
      'Notification' in window &&
      window.isSecureContext
    );
  }

  /** Détermine l'état du push sur cet appareil (sans rien demander à l'utilisateur). */
  async refreshPushState(): Promise<PushState> {
    let state: PushState;
    if (!this.pushSupported) {
      state = 'unsupported';
    } else if (Notification.permission === 'denied') {
      state = 'denied';
    } else {
      const registration = await navigator.serviceWorker.getRegistration(this.scope());
      const subscription = await registration?.pushManager.getSubscription();
      state = subscription && Notification.permission === 'granted' ? 'on' : 'off';
    }
    this.pushState.set(state);
    return state;
  }

  /** Demande l'autorisation puis abonne ce navigateur au compte connecté. */
  async enablePush(): Promise<PushState> {
    if (!this.pushSupported) {
      return this.refreshPushState();
    }
    if ((await Notification.requestPermission()) !== 'granted') {
      return this.refreshPushState();
    }
    const registration = await navigator.serviceWorker.register(new URL('push-sw.js', document.baseURI).href, {
      scope: this.scope(),
    });
    // Pas de `serviceWorker.ready` : il n'attend que le worker qui contrôle la page (ngsw).
    await waitUntilActive(registration);
    const { publicKey } = await firstValueFrom(
      this.http.get<{ publicKey: string }>(`${this.baseUrl}/push/public-key`),
    );
    const serverKey = base64UrlToBytes(publicKey);

    let subscription = await registration.pushManager.getSubscription();
    if (subscription && !sameKey(subscription.options.applicationServerKey, serverKey)) {
      // Clés du serveur changées : l'ancien abonnement ne peut plus recevoir.
      await subscription.unsubscribe();
      subscription = null;
    }
    subscription ??= await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: serverKey,
    });

    const json = subscription.toJSON();
    await firstValueFrom(
      this.http.post<void>(`${this.baseUrl}/push/subscriptions`, {
        endpoint: json.endpoint,
        p256dh: json.keys?.['p256dh'],
        auth: json.keys?.['auth'],
      }),
    );
    return this.refreshPushState();
  }

  /** Désabonne ce navigateur (aussi appelé à la déconnexion : appareil éventuellement partagé). */
  async disablePush(): Promise<PushState> {
    if (this.pushSupported) {
      const registration = await navigator.serviceWorker.getRegistration(this.scope());
      const subscription = await registration?.pushManager.getSubscription();
      if (subscription) {
        await firstValueFrom(
          this.http.post<void>(`${this.baseUrl}/push/unsubscribe`, { endpoint: subscription.endpoint }),
        ).catch(() => undefined);
        await subscription.unsubscribe();
      }
    }
    return this.refreshPushState();
  }

  sendTest(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/push/test`, {});
  }

  private scope(): string {
    return new URL('push/', document.baseURI).pathname;
  }
}

export function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = (value + '='.repeat((4 - (value.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) {
    bytes[i] = raw.charCodeAt(i);
  }
  return bytes;
}

/** pushManager.subscribe exige un worker actif. */
function waitUntilActive(registration: ServiceWorkerRegistration): Promise<void> {
  if (registration.active) {
    return Promise.resolve();
  }
  const worker = registration.installing ?? registration.waiting;
  return new Promise((resolve) => {
    if (!worker) {
      resolve();
      return;
    }
    worker.addEventListener('statechange', () => {
      if (worker.state === 'activated' || worker.state === 'redundant') {
        resolve();
      }
    });
  });
}

function sameKey(current: ArrayBuffer | null, expected: Uint8Array): boolean {
  if (!current) {
    return false;
  }
  const bytes = new Uint8Array(current);
  return bytes.length === expected.length && bytes.every((b, i) => b === expected[i]);
}
