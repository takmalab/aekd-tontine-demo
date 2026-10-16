import { SessionSummary } from '../dashboard/dashboard.model';
import { UserStatus } from '../users/user.model';

/** Miroir de MemberResponse (backend). */
export interface Member {
  id: string;
  userId: string;
  email: string;
  fullName: string;
  phone: string | null;
  joinDate: string;
  active: boolean;
  createdAt: string;
  /** Un compte PENDING_VALIDATION ne peut pas être participant (décision n° 60). */
  accountStatus: UserStatus;
}

/** Miroir de MemberUpdateRequest (backend) : seuls les champs fournis sont appliqués. */
export interface MemberUpdateRequest {
  fullName?: string;
  phone?: string | null;
  active?: boolean;
}

/**
 * Miroir de MemberSummaryResponse (backend, `GET /api/dashboard/members`).
 * Compteurs de cotisations : cotisations obligatoires de la séance actuelle
 * auxquelles le membre participe. Encours : principal approuvé moins
 * remboursements des prêts APPROVED/IN_PROGRESS.
 */
export interface MemberSummary {
  memberId: string;
  mandatoryContributionsCount: number;
  paidContributionsCount: number;
  pendingContributionsCount: number;
  lateContributionsCount: number;
  individualSavings: number;
  activeLoansCount: number;
  outstandingLoanBalance: number;
  activeSanctionsCount: number;
}

/** Miroir de MembersOverviewResponse (backend). */
export interface MembersOverview {
  currentSession: SessionSummary | null;
  members: MemberSummary[];
}
