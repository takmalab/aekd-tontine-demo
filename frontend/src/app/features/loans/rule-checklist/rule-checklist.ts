import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { LOAN_RULE_LABELS, LoanRuleEvaluation } from '../loan.model';

/**
 * Résultat de l'évaluation des règles de la politique de prêt (CLAUDE.md §21) :
 * chaque règle respectée (✓) ou non (✗), avec l'explication du backend.
 */
@Component({
  selector: 'app-rule-checklist',
  imports: [MatIconModule],
  template: `
    @if (evaluations().length === 0) {
      <p class="empty">La politique appliquée ne définit aucune règle à vérifier.</p>
    } @else {
      <p class="summary" [class.summary--ko]="failed() > 0">
        <mat-icon>{{ failed() > 0 ? 'report' : 'verified' }}</mat-icon>
        @if (failed() > 0) {
          {{ failed() }} règle{{ failed() > 1 ? 's' : '' }} non respectée{{ failed() > 1 ? 's' : '' }} sur
          {{ evaluations().length }}
        } @else {
          Toutes les règles sont respectées ({{ evaluations().length }})
        }
      </p>
      <ul class="rules">
        @for (rule of evaluations(); track rule.ruleName) {
          <li class="rule" [class.rule--ko]="!rule.respected">
            <span class="rule__icon" [attr.aria-label]="rule.respected ? 'Respectée' : 'Non respectée'">
              <mat-icon>{{ rule.respected ? 'check' : 'close' }}</mat-icon>
            </span>
            <span class="rule__text">
              <span class="rule__name">{{ label(rule.ruleName) }}</span>
              <span class="rule__message">{{ rule.message }}</span>
            </span>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .empty {
      margin: 0;
      font-size: 13px;
      color: var(--aekd-gray-muted);
    }
    .summary {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 0 0 8px;
      font-size: 13px;
      font-weight: 600;
      color: var(--aekd-gray);
    }
    .summary mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: var(--aekd-orange);
    }
    .summary--ko {
      color: var(--aekd-brown-dark);
    }
    .summary--ko mat-icon {
      color: var(--aekd-brown);
    }
    .rules {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .rule {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 8px 10px;
      border-radius: 10px;
      background: #f6f3f0;
    }
    .rule__icon {
      flex: none;
      display: grid;
      place-items: center;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: var(--aekd-gray);
      color: #fff;
    }
    .rule__icon mat-icon {
      font-size: 15px;
      width: 15px;
      height: 15px;
    }
    .rule--ko {
      background: var(--aekd-brown-soft);
    }
    .rule--ko .rule__icon {
      background: var(--aekd-brown);
    }
    .rule__text {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .rule__name {
      font-size: 13px;
      font-weight: 700;
      color: var(--aekd-gray);
    }
    .rule__message {
      font-size: 12.5px;
      line-height: 1.4;
      color: var(--aekd-gray-muted);
    }
    .rule--ko .rule__message {
      color: var(--aekd-brown-dark);
    }
  `,
})
export class RuleChecklist {
  readonly evaluations = input.required<LoanRuleEvaluation[]>();
  readonly failed = computed(() => this.evaluations().filter((r) => !r.respected).length);

  label(ruleName: string): string {
    return LOAN_RULE_LABELS[ruleName] ?? ruleName;
  }
}
