import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { driver, type Driver, type DriveStep } from 'driver.js';
import { AuthService } from './auth.service';
import { ThemeService } from './theme.service';

/** Une étape : la page où elle se joue, l'élément mis en avant (facultatif) et l'explication. */
interface TourStep {
  route: string;
  /** Repère `data-tour` de l'élément ; absent : bulle centrée sur l'écran. */
  target?: string;
  title: string;
  text: string;
  side?: 'top' | 'bottom' | 'left' | 'right';
}

export type TourKind = 'member' | 'staff';

const MEMBER_TOUR: TourStep[] = [
  {
    route: '/dashboard',
    title: 'Bienvenue dans {app} 👋',
    text: 'Cette visite vous montre en une minute comment utiliser l\'application. Vous pouvez la quitter à tout moment et la relancer depuis le menu de votre compte.',
  },
  {
    route: '/dashboard',
    target: 'shortcuts',
    title: 'Vos raccourcis',
    text: '« Cotiser » pour payer une cotisation, « Demander un prêt », « Épargner ». Le chiffre indique ce qui est en retard.',
  },
  {
    route: '/dashboard',
    target: 'tiles',
    title: 'Votre situation en un coup d\'œil',
    text: 'Cotisations de la séance actuelle, épargne, prêts et sanctions. Touchez une tuile pour voir le détail.',
  },
  {
    route: '/my-contributions',
    target: 'contribute',
    title: 'Cotiser',
    text: 'Pour chaque cotisation à payer : touchez « Cotiser », indiquez le montant, le moyen (MTN, Orange Money, espèces…) et la référence de la transaction, puis joignez la capture du paiement.',
  },
  {
    route: '/my-contributions',
    target: 'my-payments',
    title: 'Suivre vos paiements',
    text: 'Un paiement déclaré est « en attente » jusqu\'à ce que le trésorier le valide. S\'il est rejeté, le motif s\'affiche ici et vous pouvez cotiser à nouveau.',
  },
  {
    route: '/my-contributions',
    target: 'statement',
    title: 'Votre relevé',
    text: 'Téléchargez à tout moment le relevé PDF de toutes vos cotisations, de votre épargne et de vos prêts.',
  },
  {
    route: '/my-savings',
    target: 'save',
    title: 'Épargner',
    text: 'Versez librement sur votre épargne personnelle. Elle reste séparée de l\'argent commun de la tontine.',
  },
  {
    route: '/my-loans',
    target: 'request-loan',
    title: 'Demander un prêt',
    text: 'Indiquez le montant, la durée et le motif (et un avaliste si la politique le demande). L\'application vérifie les règles et vous montre le total à rembourser. Le trésorier décide ensuite.',
  },
  {
    route: '/my-loans',
    target: 'bell',
    title: 'Notifications',
    text: 'Paiement validé, prêt accordé, rappel avant l\'échéance… Tout arrive ici. Activez les notifications sur votre téléphone pour ne rien manquer.',
  },
  {
    route: '/my-loans',
    target: 'menu',
    title: 'Le menu',
    text: 'Toutes les rubriques sont dans le menu : séances, cotisations, sanctions, profil…',
  },
  {
    route: '/my-loans',
    target: 'user-menu',
    title: 'Votre compte',
    text: 'Votre profil, la déconnexion et la « Visite guidée » pour revoir ce parcours quand vous voulez. Bonne utilisation !',
  },
];

const STAFF_TOUR: TourStep[] = [
  {
    route: '/dashboard',
    title: 'Bienvenue dans l\'espace de gestion 👋',
    text: 'Cette visite présente les tâches du trésorier et des administrateurs. Vous pourrez la relancer depuis le menu de votre compte.',
  },
  {
    route: '/dashboard',
    target: 'shortcuts',
    title: 'Ce qui vous attend',
    text: 'Paiements à valider, prêts à traiter, membres en retard : le chiffre indique ce qui reste à faire. Les tuiles en dessous mènent chacune à la page détaillée.',
  },
  {
    route: '/payments',
    target: 'pending-payments',
    title: 'Valider les paiements',
    text: 'Chaque paiement déclaré par un membre arrive ici. Consultez le justificatif, puis « Valider » (l\'argent entre dans les fonds) ou « Rejeter » avec un motif que le membre verra.',
  },
  {
    route: '/sessions',
    target: 'new-session',
    title: 'Les séances',
    text: 'Créez la séance du mois et rattachez-y les cotisations avec leur échéance. Un tournoi peut aussi créer les séances automatiquement.',
  },
  {
    route: '/contributions',
    target: 'contribution-cards',
    title: 'Les cotisations',
    text: 'Ouvrez une cotisation : sur la ligne de chaque membre, vous pouvez « Enregistrer un paiement » reçu en espèces (validé tout de suite), le relancer, le sanctionner ou voir sa fiche.',
  },
  {
    route: '/contributions',
    target: 'new-contribution',
    title: 'Créer une cotisation',
    text: 'Un assistant pas à pas : montant, obligatoire ou non, participants, bénéficiaires, destination des fonds, puis activation.',
  },
  {
    route: '/sanctions',
    target: 'sanction-rules',
    title: 'Les sanctions',
    text: 'Définissez les règles (par exemple un retard de plus de 3 jours). L\'application propose les membres concernés ; c\'est vous qui appliquez la sanction.',
  },
  {
    route: '/loans',
    target: 'loan-tabs',
    title: 'Les prêts',
    text: 'Demandes à traiter, prêts en cours, remboursés… Chaque demande montre la vérification des règles de la politique avant d\'approuver ou de rejeter.',
  },
  {
    route: '/funds',
    target: 'fund-account',
    title: 'Les fonds',
    text: 'Fonds commun, épargne et prêts, sanctions, fond de caisse, ration : chaque compte est séparé et ne compte que les opérations validées.',
  },
  {
    route: '/members',
    target: 'members-list',
    title: 'Les membres',
    text: 'La situation de chaque membre pour la séance actuelle : à jour, en retard, prêts, sanctions. Touchez un membre pour voir sa fiche.',
  },
  {
    route: '/members',
    target: 'user-menu',
    title: 'C\'est parti !',
    text: 'Retrouvez cette visite (et celle de l\'espace membre) dans le menu de votre compte, rubrique « Visite guidée ».',
  },
];

const TOURS: Record<TourKind, TourStep[]> = { member: MEMBER_TOUR, staff: STAFF_TOUR };

/**
 * Visite guidée interactive (tutoriel) : met en avant les vrais éléments de l'écran, page après
 * page, avec une bulle d'explication. Proposée une seule fois à la première connexion de chaque
 * compte, et relançable depuis le menu du compte.
 */
@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly theme = inject(ThemeService);
  private active: Driver | null = null;

  /** Visite adaptée au compte : gestion pour le bureau, espace membre sinon. */
  defaultKind(): TourKind {
    return this.authService.hasAnyRole(['ADMIN', 'TRESORIER']) ? 'staff' : 'member';
  }

  /** Lance la visite si ce compte ne l'a jamais vue (ni terminée, ni refusée). */
  offerOnce(): void {
    const kind = this.defaultKind();
    if (kind === 'member' && !this.authService.hasMemberProfile()) {
      return;
    }
    if (this.read(this.storageKey(kind)) !== null) {
      return;
    }
    // Laisse la page s'afficher avant la première bulle.
    setTimeout(() => this.start(kind), 900);
  }

  start(kind: TourKind = this.defaultKind()): void {
    this.active?.destroy();
    const steps = TOURS[kind];
    const tour = driver({
      showProgress: true,
      progressText: '{{current}} / {{total}}',
      nextBtnText: 'Suivant',
      prevBtnText: 'Précédent',
      doneBtnText: 'Terminer',
      allowClose: true,
      overlayOpacity: 0.55,
      stagePadding: 6,
      stageRadius: 12,
      popoverClass: 'aekd-tour',
      steps: steps.map((s) => this.toDriverStep(s)),
      onNextClick: () => this.goTo(tour, steps, (tour.getActiveIndex() ?? 0) + 1),
      onPrevClick: () => this.goTo(tour, steps, (tour.getActiveIndex() ?? 0) - 1),
      onDestroyed: () => {
        this.write(this.storageKey(kind), new Date().toISOString());
        this.active = null;
      },
    });
    this.active = tour;
    this.router.navigateByUrl(steps[0].route).then(() => this.whenReady(steps[0]).then(() => tour.drive(0)));
  }

  /**
   * Change de page si besoin, attend que l'élément visé soit affiché, puis montre l'étape.
   * Un appui pendant ce changement est ignoré (double appui sur « Suivant » au téléphone).
   */
  private goTo(tour: Driver, steps: TourStep[], index: number): void {
    if (this.moving || index < 0) {
      return;
    }
    if (index >= steps.length) {
      tour.destroy();
      return;
    }
    this.moving = true;
    const step = steps[index];
    const current = this.router.url.split(/[?#]/)[0];
    const navigate = current === step.route ? Promise.resolve(true) : this.router.navigateByUrl(step.route);
    navigate
      .then(() => this.whenReady(step))
      .then(() => tour.moveTo(index))
      .finally(() => (this.moving = false));
  }

  private moving = false;

  /** Jusqu'à 4 s pour que l'élément apparaisse (chargement des données) ; sinon bulle centrée. */
  private whenReady(step: TourStep): Promise<void> {
    if (!step.target) {
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      const started = Date.now();
      const poll = () => {
        if (this.findTarget(step) || Date.now() - started > 4000) {
          resolve();
        } else {
          setTimeout(poll, 150);
        }
      };
      poll();
    });
  }

  private toDriverStep(step: TourStep): DriveStep {
    return {
      // Absent de la page (ex. le bouton du menu n'existe qu'au téléphone) : driver.js centre la bulle.
      element: step.target ? `[data-tour="${step.target}"]` : undefined,
      popover: { title: step.title.replace('{app}', `${this.theme.shortName()} Tontine`), description: step.text, side: step.side ?? 'bottom', align: 'center' },
    };
  }

  private findTarget(step: TourStep): HTMLElement | null {
    const candidates = Array.from(document.querySelectorAll<HTMLElement>(`[data-tour="${step.target}"]`));
    return candidates.find((el) => el.offsetParent !== null || el.getClientRects().length > 0) ?? null;
  }

  private storageKey(kind: TourKind): string {
    return `aekd_tour_${kind}_${this.authService.currentUser()?.email ?? 'anonyme'}`;
  }

  private read(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return 'indisponible'; // stockage bloqué : ne pas imposer la visite à chaque fois
    }
  }

  private write(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      // Stockage indisponible : la visite pourra être reproposée, sans gravité.
    }
  }
}
