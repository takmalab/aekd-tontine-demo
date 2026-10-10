/** Miroir de NotificationType (backend). */
export type NotificationType =
  | 'SESSION_CREATED'
  | 'SESSION_CONTRIBUTION_ADDED'
  | 'PAYMENT_VALIDATED'
  | 'PAYMENT_REJECTED'
  | 'LOAN_REPAYMENT_VALIDATED'
  | 'LOAN_REPAYMENT_REJECTED'
  | 'LOAN_REPAYMENT_RECORDED'
  | 'SAVINGS_DEPOSIT_VALIDATED'
  | 'SAVINGS_DEPOSIT_REJECTED'
  | 'LOAN_APPROVED'
  | 'LOAN_REJECTED'
  | 'SANCTION_APPLIED'
  | 'SANCTION_CANCELLED'
  | 'CONTRIBUTION_REMINDER'
  | 'SESSION_PAYOUT_RECEIVED'
  | 'MEMBER_ACTIVITY'
  | 'PAYMENT_DECLARED'
  | 'LOAN_REPAYMENT_DECLARED'
  | 'SAVINGS_DEPOSIT_DECLARED'
  | 'LOAN_REQUESTED'
  | 'USER_REGISTERED'
  | 'TOURNAMENT_SESSION_GENERATED'
  | 'TOURNAMENT_COMPLETED'
  | 'TEST';

/** Miroir de NotificationResponse (backend). */
export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  /** Route de l'application à ouvrir, ex. "/payments". */
  link: string | null;
  createdAt: string;
  read: boolean;
}

export interface NotificationSettings {
  /** Jours avant la date limite ; null = pas de rappel. */
  reminderDaysBefore: number | null;
}

/** Tonalité d'affichage : positive, négative, à traiter ou neutre. */
export type NotificationTone = 'good' | 'bad' | 'todo' | 'info';

export const NOTIFICATION_STYLE: Record<NotificationType, { icon: string; tone: NotificationTone }> = {
  SESSION_CREATED: { icon: 'event', tone: 'info' },
  SESSION_CONTRIBUTION_ADDED: { icon: 'playlist_add', tone: 'info' },
  PAYMENT_VALIDATED: { icon: 'task_alt', tone: 'good' },
  PAYMENT_REJECTED: { icon: 'cancel', tone: 'bad' },
  LOAN_REPAYMENT_VALIDATED: { icon: 'task_alt', tone: 'good' },
  LOAN_REPAYMENT_REJECTED: { icon: 'cancel', tone: 'bad' },
  LOAN_REPAYMENT_RECORDED: { icon: 'receipt_long', tone: 'good' },
  SAVINGS_DEPOSIT_VALIDATED: { icon: 'savings', tone: 'good' },
  SAVINGS_DEPOSIT_REJECTED: { icon: 'cancel', tone: 'bad' },
  LOAN_APPROVED: { icon: 'account_balance', tone: 'good' },
  LOAN_REJECTED: { icon: 'block', tone: 'bad' },
  SANCTION_APPLIED: { icon: 'gavel', tone: 'bad' },
  SANCTION_CANCELLED: { icon: 'undo', tone: 'good' },
  CONTRIBUTION_REMINDER: { icon: 'alarm', tone: 'todo' },
  SESSION_PAYOUT_RECEIVED: { icon: 'redeem', tone: 'good' },
  MEMBER_ACTIVITY: { icon: 'groups', tone: 'info' },
  PAYMENT_DECLARED: { icon: 'fact_check', tone: 'todo' },
  LOAN_REPAYMENT_DECLARED: { icon: 'fact_check', tone: 'todo' },
  SAVINGS_DEPOSIT_DECLARED: { icon: 'fact_check', tone: 'todo' },
  LOAN_REQUESTED: { icon: 'request_quote', tone: 'todo' },
  USER_REGISTERED: { icon: 'person_add', tone: 'todo' },
  TOURNAMENT_SESSION_GENERATED: { icon: 'event_repeat', tone: 'info' },
  TOURNAMENT_COMPLETED: { icon: 'flag', tone: 'info' },
  TEST: { icon: 'notifications_active', tone: 'info' },
};

/** État du push sur cet appareil. */
export type PushState = 'unsupported' | 'denied' | 'off' | 'on';
