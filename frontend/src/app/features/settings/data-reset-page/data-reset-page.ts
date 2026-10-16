import { LowerCasePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../../environments/environment';

/** Miroir de DataResetCategory (backend). */
type DataResetCategory =
  | 'NOTIFICATIONS'
  | 'TOURNAMENTS'
  | 'PAYOUTS'
  | 'ACCOUNT_OPERATIONS'
  | 'SANCTIONS'
  | 'LOANS'
  | 'LOAN_POLICIES'
  | 'SAVINGS'
  | 'CONTRIBUTIONS'
  | 'SESSIONS'
  | 'MEMBERS';

interface ResetItem {
  label: string;
  count: number;
}

interface CategoryPreview {
  category: DataResetCategory;
  label: string;
  implies: DataResetCategory[];
  items: ResetItem[];
}

interface ResetResult {
  categories: DataResetCategory[];
  deleted: ResetItem[];
  deletedFiles: number;
}

const CONFIRMATION_WORD = 'REINITIALISER';

/**
 * Réinitialisation des données (docs/decisions.md n° 63) — super administrateur.
 * Choix par catégories ; celles qui en dépendent sont cochées d'office et affichées.
 * Confirmation par mot-clé et mot de passe ; suppression définitive.
 */
@Component({
  selector: 'app-data-reset-page',
  imports: [
    LowerCasePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './data-reset-page.html',
  styleUrl: './data-reset-page.scss',
})
export class DataResetPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  private readonly baseUrl = `${environment.apiUrl}/settings/data-reset`;

  readonly confirmationWord = CONFIRMATION_WORD;
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly preview = signal<CategoryPreview[]>([]);
  readonly selected = signal<Set<DataResetCategory>>(new Set());
  readonly result = signal<ResetResult | null>(null);

  readonly confirmation = new FormControl('', { nonNullable: true, validators: Validators.required });
  readonly password = new FormControl('', { nonNullable: true, validators: Validators.required });
  private readonly confirmationValue = signal('');

  /** Catégories cochées + celles qu'elles entraînent (même calcul que le serveur). */
  readonly effective = computed(() => {
    const byCategory = new Map(this.preview().map((p) => [p.category, p]));
    const result = new Set<DataResetCategory>();
    const pending = [...this.selected()];
    while (pending.length) {
      const category = pending.pop()!;
      if (!result.has(category)) {
        result.add(category);
        pending.push(...(byCategory.get(category)?.implies ?? []));
      }
    }
    return result;
  });

  readonly labels = computed(() => new Map(this.preview().map((p) => [p.category, p.label])));

  readonly confirmationOk = computed(() => normalize(this.confirmationValue()) === CONFIRMATION_WORD);

  constructor() {
    this.confirmation.valueChanges.subscribe((v) => this.confirmationValue.set(v));
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.http.get<CategoryPreview[]>(this.baseUrl).subscribe({
      next: (preview) => {
        this.preview.set(preview);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  isSelected(category: DataResetCategory): boolean {
    return this.selected().has(category);
  }

  /** Cochée d'office par une autre catégorie (non décochable tant que celle-ci l'est). */
  isImplied(category: DataResetCategory): boolean {
    return this.effective().has(category) && !this.selected().has(category);
  }

  /** Catégories cochées qui entraînent celle-ci. */
  impliedBy(category: DataResetCategory): string {
    return this.preview()
      .filter((p) => this.effective().has(p.category) && p.implies.includes(category))
      .map((p) => this.labels().get(p.category) ?? p.category)
      .join(', ');
  }

  toggle(category: DataResetCategory, checked: boolean): void {
    const next = new Set(this.selected());
    if (checked) {
      next.add(category);
    } else {
      next.delete(category);
    }
    this.selected.set(next);
  }

  selectAll(): void {
    this.selected.set(new Set(this.preview().map((p) => p.category)));
  }

  clearAll(): void {
    this.selected.set(new Set());
  }

  total(preview: CategoryPreview): number {
    return preview.items.reduce((sum, item) => sum + item.count, 0);
  }

  reset(): void {
    if (this.effective().size === 0 || !this.confirmationOk() || this.password.invalid) {
      this.password.markAsTouched();
      this.confirmation.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.errorMessage.set(null);
    this.http
      .post<ResetResult>(this.baseUrl, {
        categories: [...this.selected()],
        confirmation: this.confirmation.value,
        password: this.password.value,
      })
      .subscribe({
        next: (result) => {
          this.saving.set(false);
          this.result.set(result);
          this.selected.set(new Set());
          this.confirmation.reset();
          this.password.reset();
          this.snackBar.open('Réinitialisation effectuée', 'OK', { duration: 5000 });
          this.load();
        },
        error: (err: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            err.status === 400 || err.status === 403
              ? err.error?.message || 'Réinitialisation refusée.'
              : 'Une erreur est survenue : rien n’a été effacé. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}

function normalize(word: string): string {
  return word
    .trim()
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toUpperCase();
}
