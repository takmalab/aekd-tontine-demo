/**
 * Intérêts d'un prêt, même règle que LoanInterest côté serveur
 * (docs/decisions.md n° 14 et 19) : taux mensuel simple sur le capital de
 * départ, pour chaque mois de la durée, arrondi aux 5 FCFA supérieurs.
 * Sert à l'affichage avant envoi ; le montant qui fait foi est celui calculé
 * par le serveur à l'approbation.
 */
export function loanInterest(capital: number | null, monthlyRatePercent: number | null, months: number | null): number {
  if (!capital || !monthlyRatePercent || monthlyRatePercent <= 0 || !months || months <= 0) {
    return 0;
  }
  // Arrondi intermédiaire : évite qu'une imprécision binaire (ex. 1000,0000001) ne passe aux 5 FCFA suivants.
  const raw = Math.round(((capital * monthlyRatePercent * months) / 100) * 1e6) / 1e6;
  return Math.ceil(raw / 5) * 5;
}
