export type RoleName = 'ADMIN' | 'TRESORIER' | 'MEMBRE';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  email: string;
  roles: RoleName[];
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
