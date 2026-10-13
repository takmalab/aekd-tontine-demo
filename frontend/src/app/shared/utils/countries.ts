/**
 * Pays proposés pour la saisie d'un numéro de téléphone (connexion par code
 * SMS). Seul le Cameroun est accepté pour l'instant (docs/decisions.md
 * n° 47) : ajouter un pays ici le rend sélectionnable dans l'interface, mais
 * le serveur doit aussi l'accepter (PhoneNumbers côté backend).
 */
export interface Country {
  /** Code ISO 3166-1 alpha-2. */
  iso: string;
  name: string;
  /** Indicatif sans le « + ». */
  dialCode: string;
  /** Nombre de chiffres du numéro national (sans l'indicatif). */
  localLength: number;
  flag: string;
  /** Exemple de numéro local, affiché comme aide à la saisie. */
  placeholder: string;
}

export const COUNTRIES: readonly Country[] = [
  {
    iso: 'CM',
    name: 'Cameroun',
    dialCode: '237',
    localLength: 9,
    flag: 'assets/flags/cm.svg',
    placeholder: '6XX XX XX XX',
  },
];

export const DEFAULT_COUNTRY = COUNTRIES[0];

/**
 * Numéro envoyé au serveur : l'indicatif du pays choisi est ajouté, sauf si
 * l'utilisateur l'a déjà saisi (« +237… », « 00237… » ou « 237 » suivi du
 * numéro national complet).
 */
export function withDialCode(raw: string, country: Country): string {
  const value = raw.trim();
  const digits = value.replace(/\D/g, '');
  if (value.startsWith('+') || value.startsWith('00')) {
    return value;
  }
  if (digits.startsWith(country.dialCode) && digits.length === country.dialCode.length + country.localLength) {
    return '+' + digits;
  }
  return '+' + country.dialCode + digits;
}

/** « +237600000003 » devient « +237 6 00 00 00 03 ». */
export function formatPhone(phone: string | null): string {
  const country = COUNTRIES.find((candidate) => phone?.startsWith('+' + candidate.dialCode));
  if (!phone || !country) {
    return phone ?? '';
  }
  const prefix = '+' + country.dialCode;
  const local = phone.slice(prefix.length);
  return `${prefix} ${local.slice(0, 1)} ${local.slice(1).replace(/(\d{2})(?=\d)/g, '$1 ')}`;
}
