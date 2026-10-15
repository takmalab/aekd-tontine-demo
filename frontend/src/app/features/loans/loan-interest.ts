/**
 * Intérêts d'un prêt, même règle que LoanInterest côté serveur
 * (docs/decisions.md n° 14, 19 et 53) : taux simple sur le capital de départ,
 * par période de `periodMonths` mois et au prorata de la durée, arrondi aux
 * 5 FCFA supérieurs.
 * Sert à l'affichage avant envoi ; le montant qui fait foi est celui calculé
 * par le serveur à l'approbation.
 */
export function loanInterest(
  capital: number | null,
  ratePercent: number | null,
  months: number | null,
  periodMonths: number | null = 1,
): number {
  if (!capital || !ratePercent || ratePercent <= 0 || !months || months <= 0) {
    return 0;
  }
  const period = Math.max(1, periodMonths ?? 1);
  // Arrondi intermédiaire : évite qu'une imprécision binaire (ex. 1000,0000001) ne passe aux 5 FCFA suivants.
  const raw = Math.round(((capital * ratePercent * months) / (100 * period)) * 1e6) / 1e6;
  return Math.ceil(raw / 5) * 5;
}
