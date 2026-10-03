# AEKD – Décisions métier

## 1. Objectif du document

Ce document recense les décisions métier qui doivent être confirmées avant ou pendant l'implémentation du projet.

### Règle importante

Claude Code ne doit pas inventer une réponse lorsqu'une décision est indiquée comme :

```text
À VALIDER
```

Dans ce cas, il doit :

1. identifier le point concerné ;
2. ne pas imposer arbitrairement une règle ;
3. demander une validation lorsque cette décision est nécessaire à l'implémentation ;
4. documenter la décision une fois qu'elle est validée.

---

# 2. Statut des décisions

Utiliser les statuts suivants :

```text
DÉCIDÉ
À VALIDER
REPORTÉ
```

---

# 3. Cotisation « Voir bébé »

## Question

Quel est le montant de la cotisation « Voir bébé » ?

## Statut

```text
À VALIDER
```

## Décision

À définir.

## Conséquence technique

Le montant doit rester configurable dans l'application.

Ne pas coder de montant fixe tant que la décision n'est pas validée.

---

# 4. Cotisation « Banque / Épargne »

## Question

Quel est le montant de la cotisation Banque / Épargne ?

## Statut

```text
À VALIDER
```

## Décision

À définir.

## Conséquence technique

Le montant doit pouvoir être configuré.

Cette cotisation doit alimenter l'épargne individuelle du membre et non le fonds commun.

---

# 5. Ration

## Question

La cotisation Ration est actuellement définie à 2 000 FCFA.

Il faut encore préciser exactement l'utilisation de cet argent.

## Statut

```text
À VALIDER
```

## Décision

Montant confirmé :

```text
2 000 FCFA
```

Destination financière précise :

```text
À VALIDER
```

---

# 6. Délais de paiement

## Question

Quelle est la date limite de paiement pour chaque cotisation ?

## Statut

```text
À VALIDER
```

## Décision

À définir.

## Conséquence technique

Le système doit être conçu pour permettre une date limite par cotisation et/ou par séance.

Ne pas supposer que toutes les cotisations ont nécessairement le même délai.

---

# 7. Règles de retard

## Question

Après combien de jours une contribution devient-elle officiellement « en retard » ?

## Statut

```text
À VALIDER
```

## Exemple non définitif

```text
Date limite + 1 jour → retard
```

ou

```text
Date limite + 3 jours → retard
```

Cet exemple ne constitue pas une règle métier.

---

# 8. Sanctions financières

## Question

Quels sont les montants des sanctions pour chaque type de retard ou d'infraction ?

## Statut

```text
À VALIDER
```

## Exemple

Une règle pourrait être :

```text
Retard supérieur à 3 jours
→ 2 000 FCFA
```

Mais cette valeur est uniquement un exemple de fonctionnement.

Elle ne doit pas être considérée comme une règle définitive sans validation.

---

# 9. Sanctions en nature

## Question

Quelles sanctions en nature peuvent être appliquées ?

## Statut

```text
À VALIDER
```

## Décision

À définir.

Le système doit cependant permettre de créer des règles de sanctions en nature avec une description libre.

---

# 10. Application automatique des sanctions

## Question

Une sanction détectée automatiquement doit-elle être appliquée immédiatement ou soumise à validation du trésorier ?

## Statut

```text
À VALIDER
```

## Options possibles

### Option A

Le système applique automatiquement la sanction.

### Option B

Le système détecte la situation et propose la sanction.

Le trésorier doit alors confirmer l'application.

La décision finale reste à valider.

---

# 11. Prêts – montant minimum

## Question

Quel est le montant minimum qu'un membre peut demander ?

## Statut

```text
À VALIDER
```

---

# 12. Prêts – montant maximum

## Question

Quel est le montant maximum qu'un membre peut demander ?

## Statut

```text
À VALIDER
```

---

# 13. Prêts – durée maximale

## Question

Quelle est la durée maximale autorisée pour un prêt ?

## Statut

```text
À VALIDER
```

---

# 14. Prêts – taux d'intérêt

## Question

Les prêts sont-ils soumis à un intérêt ? : OUI 

## Statut

```text
À VALIDER
```

Si oui :

* taux : A définir par le trésorier ;
* mode de calcul: A définir par le trésorier;
* fréquence: A définir par le trésorier ;
* éventuels frais supplémentaires: A définir par le trésorier.

---

# 15. Prêts – épargne minimale

## Question

Un membre doit-il disposer d'une épargne minimale avant de pouvoir demander un prêt ?:  A définir

## Statut

```text
À VALIDER
```

Si oui, définir :

```text
Montant minimum d'épargne
```

---

# 16. Prêts – ancienneté

## Question

Un membre doit-il avoir une ancienneté minimale dans la tontine avant de pouvoir demander un prêt ?

## Statut

```text
À VALIDER
```

Si oui, définir :

```text
Ancienneté minimale
```

---

# 17. Prêts – nombre de prêts actifs

## Question

Combien de prêts un membre peut-il avoir simultanément ?

## Statut

```text
À VALIDER
```

Exemple possible :

```text
1 prêt actif maximum
```

Cet exemple n'est pas une règle définitive.

---

# 18. Source des prêts

## Décision actuelle

Les prêts doivent être considérés comme provenant des fonds disponibles liés à l'épargne individuelle selon les règles de fonctionnement de l'AEKD.

## Statut

```text
DÉCIDÉ
```

## Point à préciser

La mécanique exacte permettant de déterminer les fonds effectivement disponibles pour les prêts reste à préciser si nécessaire.

---

# 19. Remboursement

## Question

Le membre rembourse-t-il :

* uniquement le capital ;
* capital + intérêt ;
* capital + intérêt + éventuels frais ?

## Statut

```text
À VALIDER
```

---

# 20. Paiements partiels

## Décision actuelle

Un prêt peut être remboursé en plusieurs paiements.

Exemple :

```text
Prêt : 300 000 FCFA

Remboursement 1 : 50 000 FCFA
Remboursement 2 : 100 000 FCFA
Remboursement 3 : 150 000 FCFA
```

Le système doit calculer automatiquement le solde restant.

## Statut

```text
DÉCIDÉ
```

---

# 21. Remboursement supérieur au solde

## Décision actuelle

Un remboursement supérieur au montant restant dû doit être refusé.

Exemple :

```text
Solde restant : 50 000 FCFA

Remboursement demandé : 70 000 FCFA

→ opération refusée
```

## Statut

```text
DÉCIDÉ
```

---

# 22. Validation des paiements

## Décision actuelle

Les paiements déclarés par les membres doivent être validés avant d'être comptabilisés dans les fonds.

## Statut

```text
DÉCIDÉ
```

---

# 23. Auto-validation

## Décision actuelle

Un membre ne doit pas pouvoir valider lui-même son propre paiement.

La validation doit être effectuée par un utilisateur autorisé, notamment le trésorier ou l'administrateur selon les permissions définies.

## Statut

```text
DÉCIDÉ
```

---

# 24. Justificatifs

## Décision actuelle

Le MVP accepte les formats :

```text
JPG
JPEG
PNG
PDF
```

## Stockage MVP

Stockage local.

## Architecture

Prévoir une abstraction permettant de remplacer ultérieurement le stockage local par un stockage cloud.

## Statut

```text
DÉCIDÉ
```

---

# 25. Paiements réels

## Décision actuelle

Le MVP ne doit pas intégrer directement les API :

* MTN Mobile Money ;
* Orange Money ;
* banques.

Le membre déclare manuellement son paiement.

## Statut

```text
DÉCIDÉ
```

Une abstraction de paiement doit néanmoins être prévue pour faciliter les intégrations futures.

---

# 26. Nombre de tontines

## Décision actuelle

Le MVP concerne une seule tontine pour l'instant : AEKD.

La gestion de plusieurs tontines est hors périmètre du MVP.

## Statut

```text
DÉCIDÉ
```

---

# 27. Application mobile

## Décision actuelle

Le MVP est uniquement web responsive et progressive.

Flutter/mobile est reporté à une phase ultérieure.

## Statut

```text
REPORTÉ
```

---

# 28. Notifications

## Décision actuelle

Les notifications :

* WhatsApp ;
* SMS ;
* Push ;

les membres  doivent pouvoir êtres notifiés (push) des différentes actions. On pourra intégrer les notificationw Whatsapp et SMS plustard.

## Statut

```text
VALIDER
```

---

# 29. Exports

## Question

Le MVP doit-il proposer un export des listes (Excel/CSV/PDF), initialement listé
comme hors périmètre (CLAUDE.md §3) ?

## Statut

```text
DÉCIDÉ
```

## Décision

Le propriétaire du projet a validé le 2026-09-26 la sortie du périmètre MVP
initial pour ajouter un export **CSV** côté frontend sur les écrans Membres,
Utilisateurs et Journal d'audit.

Un export Excel (`.xlsx`) reste **hors périmètre** tant qu'il n'est pas
explicitement redemandé. L'export **PDF** a été demandé le 2026-10-03 : voir la
décision n° 37.

## Conséquence technique

* `shared/utils/csv-export.ts` : génère un CSV côté navigateur (`Blob`, aucune
  dépendance) à partir des lignes déjà filtrées/affichées par l'écran — l'export
  reflète toujours exactement la recherche/les filtres actifs, jamais un
  ré-appel API séparé.
* Composant `shared/components/export-menu/` réutilisé sur les écrans concernés.

---

# 30. Réunions

## Décision actuelle

La gestion avancée des réunions/séances n'est pas prioritaire dans le MVP.

La notion de séance est toutefois obligatoire car elle permet de rattacher les cotisations et les contributions à une période.

## Statut

```text
DÉCIDÉ
```

---

# 31. Comptabilité

## Décision actuelle

Le MVP ne constitue pas un logiciel de comptabilité complet.

Il doit uniquement permettre de suivre les flux nécessaires à la gestion de la tontine :

* cotisations;
* épargne individuelle ;
* prêts ;
* remboursements ;
* sanctions financières.

## Statut

```text
DÉCIDÉ
```

---

# 32. Multi-utilisateurs

## Décision actuelle

Tous les membres de la tontine doivent pouvoir disposer d'un compte.

Les rôles principaux sont :

```text
ADMIN
TRESORIER
MEMBRE
```

## Statut

```text
DÉCIDÉ
```

---

# 33. Principes de décision

Lorsqu'une nouvelle règle métier apparaît pendant le développement, elle doit être ajoutée à ce fichier.

Format recommandé :

```text
## Nouvelle décision

### Question

...

### Statut

À VALIDER

### Décision

...

### Conséquence technique

...
```

Une décision validée doit être changée en :

```text
DÉCIDÉ
```

Une fonctionnalité volontairement repoussée doit être marquée :

```text
REPORTÉ
```

---

# 34. Règle finale

Aucune décision métier importante ne doit être prise uniquement pour faciliter le développement.

Si une décision influence :

* les montants ;
* les sanctions ;
* les prêts ;
* les cotisations ;
* les fonds ;
* les droits des membres ;
* les remboursements ;

elle doit être explicitement validée et documentée.

---

# 35. Suppression d'une séance ou d'une cotisation

## Question

Peut-on supprimer une séance ou une cotisation lorsque des paiements ou des
sanctions y sont rattachés ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validée par le propriétaire du projet le 2026-10-03.

La suppression est **refusée** dès qu'au moins un paiement (quel que soit son
statut : en attente, validé ou rejeté) ou une sanction appliquée existe. Aucune
donnée financière ne doit disparaître.

Pour une cotisation dans ce cas, l'alternative proposée est la
**désactivation** (statut `INACTIVE`, réactivable).

## Conséquence technique

* `DELETE /api/sessions/{id}` et `DELETE /api/contributions/{id}` (ADMIN, TRESORIER)
  renvoient `409` avec un message explicite si des paiements ou sanctions existent.
* Sans paiement ni sanction, la suppression entraîne celle des éléments de
  configuration rattachés (périodes, participants, bénéficiaires, règles de
  sanction) par `ON DELETE CASCADE`.
* `PUT /api/contributions/{id}/deactivate` ajouté.
* Opérations tracées dans l'audit (`SESSION_DELETED`, `CONTRIBUTION_DELETED`,
  `CONTRIBUTION_DEACTIVATED`).

---

# 36. Modification d'une séance ou d'une cotisation

## Question

Quels champs peut-on modifier, notamment sur une cotisation qui a déjà des
paiements ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validée par le propriétaire du projet le 2026-10-03.

* **Séance** : tous les champs restent modifiables (libellé, date, lieu,
  récepteur, bénéficiaires) ; aucun montant n'en dépend.
* **Cotisation** : nom, description, fréquence et visibilité restent modifiables.
  Le montant, le mode de montant, le caractère obligatoire/facultatif et la
  destination des fonds sont **verrouillés dès qu'un paiement existe**, pour ne
  pas fausser rétroactivement les statuts des membres ni les fonds.

## Conséquence technique

* `PUT /api/sessions/{id}` et `PUT /api/contributions/{id}` (ADMIN, TRESORIER) ;
  le second renvoie `409` en listant les champs verrouillés modifiés.
* `ContributionDefinitionResponse.hasPayments` permet à l'interface de griser
  ces champs et le bouton de suppression.
* Opérations tracées dans l'audit (`SESSION_UPDATED`, `CONTRIBUTION_UPDATED`).

---

# 37. Documents PDF (reçus, rapports, relevés, exports)

## Question

Faut-il générer des documents PDF aux couleurs de l'association, et lesquels ?

## Statut

```text
DÉCIDÉ
```

## Décision

Demandée et validée par le propriétaire du projet le 2026-10-03 (sortie du
périmètre MVP initial, CLAUDE.md §3). Les PDF sont générés **côté serveur**
(bibliothèque OpenPDF), avec le logo et le nom de l'association.

Premiers documents :

* **Reçus** : paiement de cotisation (uniquement s'il est **validé**) et
  remboursement de prêt (avec le solde restant après ce remboursement) ;
  montant en chiffres et en toutes lettres.
* **Rapport de séance** : pour chaque cotisation de la séance, l'état de chaque
  membre (payé, partiel, en attente, en retard, non payé), les montants et un
  récapitulatif.
* **Relevé individuel du membre** : paiements, épargne individuelle, versements
  au fonds commun, prêts, remboursements, sanctions.
* **Exports PDF des listes** (membres, utilisateurs, paiements en attente, prêts,
  sanctions, journal d'audit), qui reprennent la recherche et les filtres affichés,
  comme l'export CSV (n° 29).

## Accès

* Reçus et relevé : le membre concerné, ADMIN et TRESORIER.
* Rapport de séance et exports de listes : ADMIN et TRESORIER.

## Points à valider

```text
À VALIDER
```

* Les membres doivent-ils pouvoir télécharger le **rapport de séance**, qui
  montre la situation de tous les participants ? (Réservé au personnel en attendant.)
* Numérotation officielle des reçus : en attendant, le numéro est dérivé de
  l'identifiant (ex. `PAI-3F2A9C1B`), sans séquence continue.

## Conséquence technique

* Module backend `document/` : `AekdPdf` (gabarit commun : en-tête, pied de page
  « Page X / Y », couleurs), services par document, `DocumentController`
  (`/api/documents/...`). Logo : `backend/src/main/resources/branding/logo-aekd.png`.
* Frontend : `DocumentService` (téléchargement, nom de fichier lu dans
  `Content-Disposition`) et menu d'export CSV/PDF partagé (`ExportMenu`).

---

# 38. Remboursement d'un prêt par le membre

## Question

Le membre peut-il rembourser lui-même son prêt depuis « Mes prêts », et ce
remboursement est-il comptabilisé immédiatement ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validée par le propriétaire du projet le 2026-10-03.

* Le membre peut **déclarer** un remboursement de son propre prêt, **partiel ou
  total** (montant, opérateur, référence, date), avec un **justificatif
  facultatif** (JPG, JPEG, PNG, PDF).
* Le remboursement déclaré est **en attente** : il n'est déduit du solde qu'après
  **validation par le trésorier**, qui peut aussi le rejeter. Comme pour les
  cotisations, personne ne valide le remboursement de son propre prêt.
* Le montant déclaré ne peut pas dépasser le solde restant diminué des
  remboursements déjà en attente.
* Le trésorier peut toujours enregistrer directement un remboursement reçu : il
  est alors comptabilisé immédiatement.
* Un reçu PDF n'est disponible que pour un remboursement validé.

## Conséquence technique

* Migration V13 : colonnes `status`, `validated_by`, `validated_at` et
  justificatif sur `loan_repayment` ; les remboursements existants (saisis par le
  trésorier) sont marqués validés.
* API : `POST /api/loans/{loanId}/repayments/declare` (membre emprunteur),
  `GET /api/loans/repayments/pending`, `PUT /api/loans/repayments/{id}/validate`
  et `/reject` (TRESORIER), `POST|GET /api/loans/repayments/{id}/proof`.
* Le solde et les totaux (tableaux de bord, relevés, reçus) ne comptent que les
  remboursements validés. Audit : `LOAN_REPAYMENT_DECLARED`, `_VALIDATED`, `_REJECTED`.
* Interface : bouton « Rembourser » sur les cartes de « Mes prêts » ; section
  « Remboursements de prêts » sur la page « Paiements à valider ».

---

# 39. Épargne : versements libres et intérêts

## Question

Comment un membre épargne-t-il en dehors des séances, et comment calculer les
intérêts produits par l'épargne ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validée par le propriétaire du projet le 2026-10-03.

* **Bouton « Épargner »** : le membre déclare à tout moment un **versement libre**
  (montant, opérateur, référence, date, observation, justificatif facultatif),
  indépendant des séances. Il est compté dans l'épargne après **validation par
  le trésorier** (personne ne valide son propre versement). Plusieurs versements
  sont possibles.
* L'épargne d'un membre = versements libres validés + paiements validés des
  cotisations destinées à l'épargne individuelle (ex. « Banque / Épargne »).
  Elle reste séparée du fonds commun.
* **Intérêts** : taux **mensuel**, configurable par l'ADMIN ou le TRESORIER, sans
  valeur par défaut. Intérêts **simples**, comptés **par mois complet** écoulé
  depuis la validation du versement : montant × taux × mois complets. Les
  intérêts ne produisent pas d'intérêts.
* Un nouveau taux s'applique aux mois d'épargne qui se terminent à partir de sa
  date d'effet ; les mois précédents gardent leur taux. Tant qu'aucun taux n'est
  fixé, aucun intérêt n'est calculé.
* **Retraits** : non gérés pour l'instant (règles à définir).

## Points à valider

```text
À VALIDER
```

* Les intérêts sont calculés et affichés, mais **pas versés** : leur
  financement (par exemple les intérêts des prêts, décision n° 14) et leur
  versement effectif restent à définir.
* L'épargne minimale exigée pour un prêt (décision n° 15) est comparée au
  capital épargné, sans les intérêts.

## Conséquence technique

* Migration V14 : tables `savings_deposit` et `savings_interest_rate`.
* API `/api/savings/...` : `me/overview`, `deposits` (+ justificatif),
  `deposits/pending`, `deposits/{id}/validate|reject` (TRESORIER), `overview`,
  `entries`, `members/{id}/overview`, `rates` (ADMIN, TRESORIER).
* Le solde d'épargne utilisé partout (tableaux de bord, relevé, règle de prêt)
  inclut les versements libres validés.
* Interface : pages « Mon épargne » (membre) et « Épargnes » (ADMIN, TRESORIER).

---

# 40. L'administrateur a tous les droits

## Question

L'administrateur peut-il effectuer les actions réservées au trésorier
(validation des paiements, décisions de prêt, remboursements, sanctions,
versements d'épargne) ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validée par le propriétaire du projet le 2026-10-03 : l'**ADMIN a tous les
droits**, y compris ceux du TRESORIER. Cela remplace la répartition initiale de
CLAUDE.md §6, où l'ADMIN ne faisait que consulter les paiements et les prêts.

* La règle « personne ne valide ni ne décide pour lui-même » reste valable : un
  administrateur qui est aussi membre ne valide pas ses propres paiements,
  remboursements ou versements d'épargne.
* L'ADMIN ne devient pas MEMBRE : « Mes cotisations », « Mes prêts » et « Mon
  épargne » restent liés à l'existence d'un profil membre.
* Le TRESORIER ne gagne aucun droit d'ADMIN (utilisateurs, journal d'audit,
  politiques de prêt…).

## Conséquence technique

* Backend : hiérarchie de rôles Spring Security `ROLE_ADMIN > ROLE_TRESORIER`
  (`SecurityConfig.roleHierarchy`), appliquée aux `@PreAuthorize` et à
  `CurrentUserProvider.hasAnyRole`. Toute future règle « TRESORIER » inclut
  automatiquement l'ADMIN.
* Frontend : même hiérarchie dans `AuthService.hasRole` / `hasAnyRole`.

