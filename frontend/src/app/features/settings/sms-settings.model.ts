/** Miroir de SmsSettingsDtos (backend), docs/decisions.md n° 48. */
export interface SmsProviderInfo {
  id: string;
  label: string;
  configured: boolean;
  simulation: boolean;
}

export interface SmsSettings {
  /** Fournisseur réellement utilisé (choix de l'admin, sinon configuration du serveur). */
  activeProvider: string;
  /** Vrai si aucun choix n'a été enregistré : la configuration du serveur s'applique. */
  fromConfiguration: boolean;
  fallbackEnabled: boolean;
  providers: SmsProviderInfo[];
  updatedAt: string;
  updatedBy: string | null;
}

export interface SmsSettingsRequest {
  activeProvider: string;
  fallbackEnabled: boolean;
}

export interface SmsTestRequest {
  phone: string;
  provider?: string | null;
}

export interface SmsTestResponse {
  provider: string;
  phone: string;
  /** Code contenu dans le SMS de test, pour vérifier la réception. */
  code: string;
  message: string;
}
