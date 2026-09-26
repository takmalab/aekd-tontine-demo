import { RoleName } from '../../core/models/auth.model';

/** Miroir de UserStatus (backend). */
export type UserStatus = 'PENDING_VALIDATION' | 'ACTIVE' | 'DISABLED';

/** Miroir de UserResponse (backend). */
export interface AppUser {
  id: string;
  email: string;
  status: UserStatus;
  roles: RoleName[];
  memberId: string | null;
  memberFullName: string | null;
  createdAt: string;
}

/** Miroir de MemberProfileRequest (backend). */
export interface MemberProfileRequest {
  fullName: string;
  phone?: string | null;
  joinDate: string;
}

/** Miroir de CreateUserRequest (backend). */
export interface CreateUserRequest {
  email: string;
  password: string;
  roles: RoleName[];
  member?: MemberProfileRequest | null;
}

/** Miroir de UpdateUserRequest (backend) : seuls les champs fournis sont appliqués. */
export interface UpdateUserRequest {
  status?: UserStatus;
  roles?: RoleName[];
}

export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  PENDING_VALIDATION: 'En attente de validation',
  ACTIVE: 'Actif',
  DISABLED: 'Désactivé',
};

export const ROLE_LABELS: Record<RoleName, string> = {
  ADMIN: 'Administrateur',
  TRESORIER: 'Trésorier',
  MEMBRE: 'Membre',
};
