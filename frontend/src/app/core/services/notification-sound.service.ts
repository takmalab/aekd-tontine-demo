import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'aekd_notification_sound';

/**
 * Petit carillon joué par l'application quand une nouvelle notification arrive
 * pendant qu'elle est ouverte. Synthétisé avec Web Audio (aucun fichier audio).
 * Les navigateurs n'autorisent le son qu'après une interaction de l'utilisateur
 * avec la page : le contexte audio est donc débloqué au premier clic ou touche.
 * Préférence propre à chaque appareil (localStorage).
 */
@Injectable({ providedIn: 'root' })
export class NotificationSoundService {
  readonly enabled = signal(readPreference());

  private context: AudioContext | null = null;

  constructor() {
    if (typeof document !== 'undefined') {
      const unlock = () => {
        this.audioContext()?.resume().catch(() => undefined);
        document.removeEventListener('pointerdown', unlock);
        document.removeEventListener('keydown', unlock);
      };
      document.addEventListener('pointerdown', unlock);
      document.addEventListener('keydown', unlock);
    }
  }

  setEnabled(enabled: boolean): void {
    this.enabled.set(enabled);
    try {
      localStorage.setItem(STORAGE_KEY, enabled ? 'on' : 'off');
    } catch {
      // Stockage indisponible (navigation privée…) : la préférence vaut pour la session.
    }
  }

  /** Joue le carillon si le son est activé ; sans effet si le navigateur le bloque encore. */
  play(): void {
    if (!this.enabled()) {
      return;
    }
    const ctx = this.audioContext();
    if (!ctx) {
      return;
    }
    const start = () => {
      const now = ctx.currentTime;
      this.tone(ctx, 880, now, 0.16);
      this.tone(ctx, 1318.5, now + 0.14, 0.28);
    };
    if (ctx.state === 'suspended') {
      ctx.resume().then(start).catch(() => undefined);
    } else {
      start();
    }
  }

  private tone(ctx: AudioContext, frequency: number, at: number, duration: number): void {
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.value = frequency;
    // Attaque douce et extinction progressive : pas de « clic » audible.
    gain.gain.setValueAtTime(0.0001, at);
    gain.gain.exponentialRampToValueAtTime(0.25, at + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, at + duration);
    oscillator.connect(gain).connect(ctx.destination);
    oscillator.start(at);
    oscillator.stop(at + duration + 0.02);
  }

  private audioContext(): AudioContext | null {
    if (this.context) {
      return this.context;
    }
    const Ctor =
      typeof window !== 'undefined'
        ? (window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext)
        : undefined;
    this.context = Ctor ? new Ctor() : null;
    return this.context;
  }
}

function readPreference(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'off';
  } catch {
    return true;
  }
}
