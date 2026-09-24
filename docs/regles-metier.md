# AEKD – Règles métier

## 1. Objet

Ce document décrit les règles métier de l'application AEKD – Gestion de Tontine.

Il constitue la référence fonctionnelle pour le développement.

Toute règle non définie dans ce document doit être considérée comme une **décision à valider** et ne doit pas être inventée par le développeur ou l'IA.

---

# 2. Organisation de la tontine

L'AEKD fonctionne autour d'une seule tontine.

Le nombre de membres est inférieur à 30. Mais il pourra augmenter à l'avanir.

Tous les membres disposent d'un compte utilisateur permettant d'accéder à l'application.

Les rôles principaux sont :

* ADMIN
* TRESORIER
* MEMBRE

---

# 3. Séances

La tontine fonctionne avec des séances périodiques, notamment mensuelles. Mais on pourra également avoir des séances hebdomaires ou journalière, tout dépend des règles fixés par la tontine.

Exemples :

* Séance de septembre 2026
* Séance d'octobre 2026
* Séance de novembre 2026

Une séance constitue le contexte dans lequel les cotisations sont dues.

Une même séance peut contenir plusieurs cotisations.

Les cotisations doivent rester distinctes même lorsqu'elles sont dues au même moment.

---

# 4. Cotisation

Une cotisation est une règle/configuration permettant de définir une somme ou une participation demandée aux membres.

Une cotisation possède notamment :

* un nom ;
* une description ;
* un montant ;
* une fréquence ;
* un mode de montant ;
* un caractère obligatoire ou facultatif ;
* une visibilité ;
* des participants ;
* des bénéficiaires ;
* des règles de sanction ;
* une destination des fonds ;
* un statut.

Une cotisation n'est pas un paiement.

Il faut distinguer :

```text
Cotisation
↓
Période de cotisation
↓
Déclaration de paiement(Cotiser)
↓
Validation du paiement
```

---

# 5. Cotisations connues

Les cotisations actuellement identifiées sont les suivantes.

## 5.1 Cotisation 50 000 FCFA

Caractéristiques :

* montant : 50 000 FCFA ;
* fréquence : mensuelle / par séance ;
* obligatoire : oui ;
* destination : fonds commun de la tontine.

---

## 5.2 Cotisation 10 000 FCFA

Caractéristiques :

* montant : 10 000 FCFA ;
* fréquence : mensuelle / par séance ;
* obligatoire : oui ;
* destination : fonds commun de la tontine.

---

## 5.3 Ration

Caractéristiques :

* montant : 2 000 FCFA ;
* fréquence : mensuelle / par séance ;
* obligatoire : oui ;
* destination : fonds commun de la tontine.

---

## 5.4 Voir bébé

Caractéristiques connues :

* fréquence : mensuelle / par séance ;
* obligatoire : non ;
* montant : à définir ;
* destination : à confirmer.

Cette cotisation doit être configurable afin que son montant puisse être défini ultérieurement.

---

## 5.5 Banque / Épargne

Caractéristiques connues :

* fréquence : mensuelle / par séance ;
* obligatoire : non ;
* montant : à définir/configurable ;
* destination : épargne individuelle du membre.

Les montants versés dans cette cotisation ne doivent pas être comptabilisés comme faisant partie du fonds commun de la tontine.

---

# 6. Montant obligatoire connu

Les trois cotisations obligatoires actuellement définies sont :

```text
50 000 FCFA
+
10 000 FCFA
+
2 000 FCFA
=
62 000 FCFA
```

Le montant obligatoire actuellement connu par séance est donc de **62 000 FCFA par membre participant**, hors cotisations facultatives.

IMPORTANT :

Les 62 000 FCFA ne constituent pas une cotisation unique.

Il faut conserver trois cotisations distinctes :

```text
Cotisation 50 000
Cotisation 10 000
Ration 2 000
```

Cela permet de gérer séparément :

* les participants ;
* les bénéficiaires ;
* les sanctions ;
* les règles ;
* les paiements ;
* la destination des fonds.

---

# 7. Fréquence des cotisations

Une cotisation peut être configurée avec une fréquence.

Le besoin actuel principal est :

```text
MONTHLY
```

La cotisation est alors applicable à chaque séance mensuelle concernée.

L'architecture doit toutefois permettre d'ajouter d'autres fréquences ultérieurement si nécessaire.

---

# 8. Montant fixe ou montant libre

Une cotisation peut utiliser différents modes de montant.

## FIXED

Le montant est imposé par la configuration.

Exemple :

```text
Cotisation 50 000 FCFA
Montant = 50 000 FCFA
```

## VOLUNTARY

Le membre peut déclarer un montant selon les règles de la cotisation.

Le système doit enregistrer le montant réellement déclaré.

Le mode exact de validation d'un montant volontaire pourra être précisé ultérieurement si nécessaire.

---

# 9. Cotisation obligatoire ou facultative

Une cotisation peut être :

```text
OBLIGATORY
OPTIONAL
```

## Obligatoire

Le membre participant doit normalement effectuer le paiement dans le délai prévu.

Une absence de paiement peut entraîner un statut de retard et éventuellement une sanction si une règle de sanction correspondante existe.

## Facultative

Le membre peut choisir de participer ou non au paiement.

Une cotisation facultative non payée ne doit pas automatiquement être considérée comme une infraction.

Elle ne doit donc pas générer automatiquement une sanction pour simple absence de paiement.

---

# 10. Participants

Chaque cotisation possède une liste de participants.

Une cotisation peut concerner :

* tous les membres ;
* un sous-ensemble des membres.

Le trésorier ou l'administrateur peut sélectionner les participants.

Un membre qui n'est pas participant à une cotisation ne doit pas pouvoir déclarer un paiement pour cette cotisation.

La participation à une cotisation est indépendante du rôle du membre.

---

# 11. Visibilité

Une cotisation peut avoir deux niveaux de visibilité :

```text
PUBLIC
PRIVATE
```

## PUBLIC

Tous les membres peuvent voir l'existence et les informations générales de la cotisation.

Cependant, la visibilité publique ne signifie pas automatiquement que tous les membres sont participants.

## PRIVATE

La cotisation est visible uniquement par :

* ADMIN ;
* TRESORIER ;
* participants sélectionnés.

---

# 12. Bénéficiaires

Le ou les bénéficiaires sont des participants.

Une cotisation peut avoir :

* zéro bénéficiaire ;
* un bénéficiaire ;
* plusieurs bénéficiaires.

---

# 13. Période de cotisation

Pour chaque séance, le système doit pouvoir déterminer la situation de chaque participant pour chaque cotisation.

Exemple :

```text
Séance octobre 2026
    ↓
Cotisation 50 000
    ↓
Membre A → payé
Membre B → en attente
Membre C → en retard
Membre D → non payé
```

Les états possibles sont notamment :

```text
PENDING
PAID
LATE
NOT_PAID
```

Le système doit pouvoir déterminer automatiquement l'état selon :

* la cotisation ;
* la séance ;
* le participant ;
* les paiements validés ;
* les dates limites.

---

# 14. Déclaration d'un paiement(Cotiser)

Un membre participant peut déclarer un paiement.

La déclaration doit contenir au minimum :

* cotisation ;
* séance/période ;
* montant ;
* opérateur ;
* référence de transaction ;
* date de paiement.

Elle peut également contenir :

* observation ;
* justificatif.

---

# 15. Opérateurs de paiement

Les opérateurs pris en compte dans le MVP sont :

```text
MTN_MOMO
ORANGE_MONEY
BANK_TRANSFER
CASH
```

Le système ne réalise pas réellement le paiement.

Le membre indique simplement comment il a effectué le paiement.

---

# 16. Statut d'un paiement

Un paiement déclaré n'est pas immédiatement considéré comme validé.

Il possède un statut :

```text
PENDING
VALIDATED
REJECTED
```

## PENDING

Le membre a déclaré le paiement(Cotisé).

Le trésorier doit encore le vérifier.

## VALIDATED

Le trésorier a vérifié et accepté le paiement.

Le montant peut alors être comptabilisé dans les fonds concernés.

## REJECTED

Le paiement a été refusé.

Le montant ne doit pas être comptabilisé dans les fonds.

---

# 17. Validation des paiements

Le trésorier ou l'administrateur autorisé peut :

* consulter le paiement ;
* consulter le justificatif ;
* valider le paiement ;
* rejeter le paiement.

Lorsqu'un paiement est validé :

```text
Paiement VALIDATED
        ↓
Mise à jour du fonds concerné
        ↓
Mise à jour du statut de la contribution
        ↓
Enregistrement dans l'audit
```

---

# 18. Justificatifs

Un membre peut joindre une preuve de paiement.

Formats acceptés dans le MVP :

```text
JPG
JPEG
PNG
PDF
```

Le système doit conserver le lien ou l'emplacement du fichier.

Le justificatif doit être consultable par les utilisateurs autorisés.

---

# 19. Destination des fonds

Chaque cotisation possède une destination financière.

Deux destinations principales sont prévues :

```text
TONTINE_FUND
INDIVIDUAL_SAVINGS
```

---

# 20. Fonds commun

Le fonds commun représente l'argent appartenant au fonctionnement collectif de la tontine.

Les cotisations actuellement affectées au fonds commun sont :

* 50 000 FCFA ;
* 10 000 FCFA ;
* Ration.

Seuls les paiements validés doivent être comptabilisés.

---

# 21. Épargne individuelle

L'épargne individuelle appartient au membre concerné.

La cotisation :

```text
Banque / Épargne
```

alimente l'épargne individuelle du membre.

Elle ne doit pas être ajoutée au fonds commun.

Le système doit permettre de connaître le solde d'épargne individuelle de chaque membre.

---

# 22. Séparation financière

Le système doit toujours distinguer :

```text
Fonds commun de la tontine
```

et

```text
Épargne individuelle des membres
```

Exemple :

```text
Membre A verse 10 000 FCFA à Banque / Épargne

→ Épargne individuelle de A : +10 000 FCFA
→ Fonds commun : +0 FCFA
```

Inversement :

```text
Membre A verse 50 000 FCFA de cotisation

→ Fonds commun : +50 000 FCFA
→ Épargne individuelle : +0 FCFA
```

---

# 23. Sanctions

Les sanctions sont liées aux règles des cotisations.

Il faut distinguer :

```text
SanctionRule
```

et

```text
AppliedSanction
```

## SanctionRule

Une règle définit dans quelles circonstances une sanction peut être appliquée.

Exemple :

```text
Retard supérieur à 3 jours
→ 2 000 FCFA
```

## AppliedSanction

Il s'agit de la sanction réellement appliquée à un membre.

Elle doit conserver le contexte de son application.

---

# 24. Types de sanctions

Deux types sont prévus :

```text
MONETARY
IN_KIND
```

## MONETARY

La sanction correspond à un montant financier.

Exemple :

```text
2 000 FCFA
```

## IN_KIND

La sanction correspond à une obligation en nature.

Elle doit être décrite textuellement.

Exemple :

```text
Apporter une quantité déterminée de produits pour la prochaine séance.
```

Les règles exactes doivent être définies par les responsables de la tontine.

---

# 25. Retards

Une contribution obligatoire peut être considérée comme en retard lorsque :

* la date limite est dépassée ;
* aucun paiement validé ne couvre la contribution.

Le système doit pouvoir détecter les retards automatiquement.

Cependant, les délais exacts doivent être configurables.

---

# 26. Sanction automatique

Le système peut identifier automatiquement qu'une règle de sanction est applicable.

Exemple :

```text
Cotisation obligatoire
+
date limite dépassée de plus de 3 jours
+
paiement non validé
=
règle de sanction applicable
```

La sanction réellement appliquée doit rester traçable.

Une cotisation facultative non payée ne doit pas déclencher automatiquement une sanction uniquement parce qu'elle n'a pas été payée.

---

# 27. Prêts

Le système permet aux membres de demander des prêts.

Les règles d'attribution des prêts sont configurables.

Elles ne doivent pas être codées en dur dans le programme.

---

# 28. Politique de prêt

Une politique de prêt peut définir :

* montant minimum ;
* montant maximum ;
* durée maximale ;
* taux d'intérêt ;
* épargne minimale ;
* ancienneté minimale comme membre ;
* nombre maximal de prêts actifs.

Une politique peut être activée ou désactivée.

---

# 29. Demande de prêt

Un membre peut effectuer une demande contenant :

* montant demandé ;
* durée souhaitée ;
* motif.

Le système vérifie les règles de la politique active.

Pour chaque règle, le système doit pouvoir déterminer :

```text
RESPECTEE
NON_RESPECTEE
```

Le système doit pouvoir expliquer pourquoi une demande ne respecte pas une règle.

---

# 30. Décision sur un prêt

Le trésorier ou l'administrateur autorisé peut :

* approuver ;
* rejeter.

La décision doit conserver :

* montant demandé ;
* montant approuvé ;
* durée demandée ;
* durée approuvée ;
* date de décision ;
* utilisateur ayant pris la décision ;
* motif de rejet si nécessaire.

---

# 31. États d'un prêt

Les états prévus sont :

```text
REQUESTED
APPROVED
IN_PROGRESS
REJECTED
REPAID
```

Un prêt approuvé peut passer à `IN_PROGRESS` lorsqu'il est effectivement en cours.

Lorsque la totalité du montant dû est remboursée :

```text
REPAID
```

---

# 32. Remboursements

Un membre peut effectuer plusieurs remboursements pour un même prêt.

Chaque remboursement contient notamment :

* montant ;
* date ;
* opérateur ;
* référence ;
* justificatif éventuel.

Le système calcule :

```text
Montant restant =
Montant dû
-
Total des remboursements validés
```

Un remboursement ne doit pas pouvoir dépasser le solde restant.

---

# 33. État financier d'un membre

Le système doit pouvoir présenter séparément :

```text
Épargne individuelle
Cotisations versées au fonds commun
Prêts en cours
Montant restant à rembourser
Sanctions monétaires
```

Ces montants ne doivent pas être mélangés.

---

# 34. Audit

Les opérations sensibles doivent être enregistrées.

Exemples :

* création d'une séance ;
* modification d'une séance ;
* création d'une cotisation ;
* modification d'une cotisation ;
* activation d'une cotisation ;
* déclaration d'un paiement ;
* validation d'un paiement ;
* rejet d'un paiement ;
* application d'une sanction ;
* demande de prêt ;
* approbation d'un prêt ;
* rejet d'un prêt ;
* remboursement.

Chaque événement doit permettre d'identifier :

* l'utilisateur ;
* la date ;
* l'action ;
* l'objet concerné ;
* l'identifiant de l'objet ;
* une description.

---

# 35. Règle générale de sécurité métier

Un membre ne doit jamais pouvoir :

* consulter les données privées d'un autre membre ;
* modifier le paiement d'un autre membre sans autorisation ;
* valider son propre paiement ;
* modifier une décision prise par le trésorier sans autorisation ;
* consulter une cotisation privée dont il n'est pas participant ;
* déclarer une cotisation à laquelle il ne participe pas.

Les utilisateurs administratifs disposent de permissions supplémentaires selon leur rôle.

---

# 36. Règle générale sur les montants

Tous les montants financiers doivent être stockés avec une précision adaptée aux FCFA.

Éviter d'utiliser des types flottants (`float`, `double`) pour les montants financiers.

Utiliser un type approprié tel que :

```text
BigDecimal
```

ou un stockage entier en FCFA si cela est justifié par l'architecture.

---

# 37. Règle de non-invention

Lorsque le système rencontre une règle métier non définie dans ce document, il ne doit pas inventer une valeur.

Il doit la signaler sous la forme :

```text
DECISION A VALIDER
```

Exemples :

* montant de Voir bébé ;
* montant de Banque / Épargne ;
* seuil de retard ;
* montant d'une sanction ;
* taux d'intérêt ;
* durée maximale d'un prêt ;
* montant maximal d'un prêt ;
* conditions exactes d'accès à un prêt.

La décision doit être validée avant de devenir une règle métier définitive.
