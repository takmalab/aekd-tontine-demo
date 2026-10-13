import { Component, ElementRef, OnInit, forwardRef, input, output, signal, viewChildren } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Saisie d'un code à usage unique, un chiffre par case. Avance
 * automatiquement, revient en arrière avec Retour arrière, accepte le
 * collage d'un code complet et l'auto-remplissage du SMS (one-time-code).
 * Émet {@link completed} quand toutes les cases sont remplies.
 */
@Component({
  selector: 'app-otp-input',
  templateUrl: './otp-input.html',
  styleUrl: './otp-input.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => OtpInput), multi: true }],
})
export class OtpInput implements ControlValueAccessor, OnInit {
  readonly length = input(6);
  readonly invalid = input(false);
  readonly completed = output<string>();

  readonly digits = signal<string[]>([]);
  readonly disabled = signal(false);
  private readonly boxes = viewChildren<ElementRef<HTMLInputElement>>('box');

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  ngOnInit(): void {
    // Les inputs signal ne sont connus qu'après la construction.
    if (this.digits().length !== this.length()) {
      this.digits.set(Array(this.length()).fill(''));
    }
  }

  writeValue(value: string | null): void {
    const chars = (value ?? '').replace(/\D/g, '').slice(0, this.length()).split('');
    this.digits.set(Array.from({ length: this.length() }, (_, i) => chars[i] ?? ''));
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

  focusFirstEmpty(): void {
    const index = this.digits().findIndex((digit) => !digit);
    this.focus(index === -1 ? this.length() - 1 : index);
  }

  onInput(index: number, event: Event): void {
    const field = event.target as HTMLInputElement;
    const typed = field.value.replace(/\D/g, '');
    if (typed.length > 1) {
      // Auto-remplissage du SMS ou saisie rapide : répartir sur les cases suivantes.
      this.fillFrom(index, typed);
      return;
    }
    this.setDigit(index, typed);
    field.value = typed;
    if (typed) {
      this.focus(index + 1);
    }
  }

  onKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace' && !this.digits()[index] && index > 0) {
      event.preventDefault();
      this.setDigit(index - 1, '');
      this.focus(index - 1);
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.focus(index - 1);
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.focus(index + 1);
    }
  }

  onPaste(index: number, event: ClipboardEvent): void {
    const pasted = (event.clipboardData?.getData('text') ?? '').replace(/\D/g, '');
    if (pasted) {
      event.preventDefault();
      this.fillFrom(index, pasted);
    }
  }

  onBlur(): void {
    this.onTouched();
  }

  private fillFrom(index: number, value: string): void {
    const next = [...this.digits()];
    value
      .slice(0, this.length() - index)
      .split('')
      .forEach((digit, offset) => (next[index + offset] = digit));
    this.digits.set(next);
    this.emit();
    this.focus(Math.min(index + value.length, this.length() - 1));
  }

  private setDigit(index: number, digit: string): void {
    const next = [...this.digits()];
    next[index] = digit;
    this.digits.set(next);
    this.emit();
  }

  private emit(): void {
    const code = this.digits().join('');
    this.onChange(code);
    if (code.length === this.length()) {
      this.completed.emit(code);
    }
  }

  private focus(index: number): void {
    const box = this.boxes()[index]?.nativeElement;
    if (box) {
      box.focus();
      box.select();
    }
  }
}
