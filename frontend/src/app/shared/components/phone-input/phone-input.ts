import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { COUNTRIES, Country, DEFAULT_COUNTRY, withDialCode } from '../../utils/countries';

/**
 * Saisie d'un numéro de téléphone : pays (drapeau + indicatif, liste
 * extensible dans utils/countries.ts), numéro national, bouton d'effacement.
 * La valeur du contrôle est le numéro complet avec indicatif (« +2376… »),
 * ou une chaîne vide.
 */
@Component({
  selector: 'app-phone-input',
  imports: [MatIconModule, MatMenuModule],
  templateUrl: './phone-input.html',
  styleUrl: './phone-input.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PhoneInput), multi: true }],
})
export class PhoneInput implements ControlValueAccessor {
  /** Message d'erreur affiché sous le champ (le champ passe alors en rouge). */
  readonly error = input<string | null>(null);

  readonly countries = COUNTRIES;
  readonly country = signal<Country>(DEFAULT_COUNTRY);
  readonly national = signal('');
  readonly disabled = signal(false);

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    const raw = value ?? '';
    const match = COUNTRIES.find((candidate) => raw.startsWith('+' + candidate.dialCode));
    if (match) {
      this.country.set(match);
      this.national.set(raw.slice(match.dialCode.length + 1));
    } else {
      this.national.set(raw);
    }
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  onInput(event: Event): void {
    this.national.set((event.target as HTMLInputElement).value);
    this.emit();
  }

  selectCountry(country: Country): void {
    this.country.set(country);
    this.emit();
  }

  clear(input: HTMLInputElement): void {
    this.national.set('');
    this.emit();
    input.focus();
  }

  onBlur(): void {
    this.onTouched();
  }

  private emit(): void {
    const national = this.national().trim();
    this.onChange(national ? withDialCode(national, this.country()) : '');
  }
}
