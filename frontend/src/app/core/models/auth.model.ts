export type RoleName = 'ADMIN' | 'TRESORIER' | 'MEMBRE';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  email: string;
  roles: RoleName[];
  /** Jeton de renouvellement : garde la session 30 jours sans activité (decisions.md n° 54). */
  refreshToken: string;
}

/** Connexion par code SMS (docs/decisions.md n° 47). */
export interface OtpRequest {
  phone: string;
}

export interface OtpRequestResponse {
  phone: string;
  message: string;
  expiresInSeconds: number;
  resendAfterSeconds: number;
}

export interface OtpVerifyRequest {
  phone: string;
  code: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}

export interface RegisterResponse {
  email: string;
  message: string;
}

export interface CurrentUser {
  email: string;
  roles: RoleName[];
}
