/** SUPER_ADMIN : tous les droits de l'ADMIN et les paramètres du système (decisions.md n° 56). */
export type RoleName = 'SUPER_ADMIN' | 'ADMIN' | 'TRESORIER' | 'MEMBRE';

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
  /** Le compte a un profil membre (T1) : évite d'appeler /members/mine pour rien. */
  hasMemberProfile?: boolean;
}

/** Connexion par code SMS (docs/decisions.md n° 47). */
export interface OtpRequest {
  phone: string;
}

/** Code envoyé par e-mail (decisions.md n° 55) : réponse identique que l'adresse ait un compte ou non. */
export interface EmailCodeSent {
  email: string;
  message: string;
  expiresInSeconds: number;
  resendAfterSeconds: number;
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
  /** Absent pour une session ouverte avant cette version : déduit du rôle MEMBRE. */
  hasMemberProfile?: boolean;
}
