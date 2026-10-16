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
DÉCIDÉ (mode de calcul) — taux À DÉFINIR par le trésorier
```

## Décision

Validé par le propriétaire du projet le 2026-10-14 :

* **taux** : défini par le trésorier dans la politique de prêt ; c'est un taux
  **mensuel** en % ;
* **mode de calcul** : intérêts **simples**, sur le **capital de départ**, pour
  chaque mois de la durée accordée :
  intérêts = capital × taux × nombre de mois ;
  exemple : 300 000 FCFA sur 6 mois à 5 % → 90 000 FCFA d'intérêts ;
* **arrondi** : aux **5 FCFA supérieurs** ;
* le taux est **figé sur le prêt à l'approbation** : modifier la politique
  ensuite ne change pas les prêts déjà accordés ;
* seuls les prêts approuvés **à partir du 2026-10-14** portent des intérêts ;
  les prêts accordés avant restent sans intérêt ;
* le membre voit le **total à rembourser** dès sa demande (estimation avec le
  taux actuel), puis le montant fixé à l'approbation.

## Points à valider

```text
À VALIDER
```

* valeur du taux ;
* éventuels frais supplémentaires (aucun pour l'instant) ;
* remboursement anticipé : les intérêts restent dus en totalité (calcul actuel)
  ou sont réduits ?

## Conséquence technique (réalisée le 2026-10-14)

* `LoanInterest` (serveur) et `loanInterest` (interface), même règle ;
  migration V21 : `loan.interest_rate` et `loan.interest_amount`.

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

Précisé le 2026-10-05 : voir la décision n° 46.

---

# 19. Remboursement

## Question

Le membre rembourse-t-il :

* uniquement le capital ;
* capital + intérêt ;
* capital + intérêt + éventuels frais ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-14 :

* le membre rembourse **capital + intérêts** (décision n° 14) ; aucun frais
  pour l'instant ;
* solde restant = capital + intérêts − remboursements validés ; le prêt passe à
  REPAID quand il atteint zéro ;
* chaque remboursement est réparti **proportionnellement** entre capital et
  intérêts (ex. 390 000 dus dont 90 000 d'intérêts : 23 % de chaque versement
  sont des intérêts) ; seule la part de capital redevient disponible pour
  prêter (décision n° 46).

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
DÉCIDÉ
```

Mis en œuvre le 2026-10-05 : voir la décision n° 44. WhatsApp et SMS restent
reportés.

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
* Contrôle « pas pour soi-même » étendu le 2026-10-05 à l'approbation et au rejet
  d'un prêt (`LoanService`) et à la saisie directe d'un remboursement
  (`LoanRepaymentService.record`) : un trésorier ou administrateur emprunteur
  déclare son remboursement, qui est validé par un autre membre du bureau.
  Étendu le même jour aux **sanctions** (validé par le propriétaire du projet) :
  personne n'applique ni n'annule une sanction qui le concerne.

---

# 41. Suppression d'un compte utilisateur

## Question

L'administrateur peut-il supprimer un compte, et que deviennent ses données ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validée par le propriétaire du projet le 2026-10-03 : l'ADMIN peut supprimer
définitivement un compte. La suppression efface **le compte, son profil membre et
tout son historique** : paiements et justificatifs, participations, prêts et
remboursements, sanctions, versements d'épargne. Les fonds et totaux sont
recalculés sans ces opérations. Action irréversible.

* Les données des **autres** membres sont conservées : là où le compte supprimé
  apparaît comme auteur (validation, enregistrement, sanction appliquée, taux…),
  l'auteur est effacé et affiché « utilisateur supprimé ».
* L'entrée d'audit `USER_DELETED` décrit ce qui a été supprimé ; les anciennes
  entrées d'audit du compte sont conservées sans auteur.
* Garde-fous : on ne supprime ni son propre compte, ni le dernier administrateur.
* Alternative non destructive toujours disponible : désactiver le compte.

## Conséquence technique

* Migration V15 : colonnes « auteur » facultatives avec `ON DELETE SET NULL`
  (validated_by, decision_by, recorded_by, applied_by, uploaded_by, declared_by,
  created_by, audit_log.user_id).
* API : `GET /api/users/{id}/deletion-impact` et `DELETE /api/users/{id}` (ADMIN).
* Interface : bouton « Supprimer » sur la page Utilisateurs ; la fenêtre affiche
  ce qui sera effacé et exige de retaper l'e-mail du compte.

---

# 42. Choix des cotisations lors de la création d'une séance

## Question

Comment rattacher des cotisations à une séance sans recréer une cotisation pour
chaque séance ?

## Statut

```text
DÉCIDÉ
```

## Décision

Demandée par le propriétaire du projet le 2026-10-05.

* Une cotisation reste un **modèle réutilisable** (montant, participants,
  bénéficiaires, sanctions…). À la création ou à la modification d'une séance, le
  trésorier ou l'administrateur **coche** les cotisations dues lors de cette
  séance. Les cotisations **diffèrent d'une séance à l'autre** : rien n'est
  pré-coché.
* Seules les cotisations **actives** peuvent être rattachées. Une cotisation déjà
  rattachée reste affichée même si elle a été désactivée depuis.
* Chaque cotisation rattachée peut avoir une **date limite facultative** propre.
  Aucune date n'est calculée automatiquement (le délai de paiement reste à
  valider, décision n° 6).
* **Retirer** une cotisation d'une séance est refusé dès qu'un paiement (quel que
  soit son statut) ou une sanction appliquée existe pour cette cotisation sur
  cette séance, comme pour la suppression (décision n° 35).
* L'assistant de création de cotisation garde son étape « séance » facultative.

## Points à valider

```text
À VALIDER
```

* La date limite d'une cotisation reste modifiable sur la séance même si des
  paiements existent déjà : le changement peut faire passer des membres en retard
  (ou les en sortir). Faut-il la verrouiller dans ce cas ?
* Création automatique des séances : voir la décision n° 43.

## Conséquence technique

* `SessionRequest.contributions` (liste de `{contributionDefinitionId, dueDate}`) sur
  `POST /api/sessions` et `PUT /api/sessions/{id}`. En modification, la liste
  remplace les rattachements actuels ; absente (`null`), ils restent inchangés.
  Les périodes conservées gardent leur identifiant (paiements préservés).
* Séance et rattachements sont enregistrés dans la même transaction : tout est
  refusé si un rattachement est invalide (`400`) ou un retrait impossible (`409`).
* Audit : `SESSION_CONTRIBUTION_ADDED`, `SESSION_CONTRIBUTION_REMOVED`,
  `SESSION_CONTRIBUTION_DUE_DATE_CHANGED`.
* Interface : section « Cotisations de la séance » dans la fenêtre de création ou
  de modification d'une séance (cases à cocher, date limite, total obligatoire
  indicatif) ; bouton « Choisir les cotisations » sur une séance sans cotisation.

---

# 43. Création automatique des séances : les tournois

## Question

Comment créer automatiquement les séances, avec leur récepteur, leurs
bénéficiaires et leurs cotisations ?

## Statut

```text
DÉCIDÉ
```

## Décision

Choix du propriétaire du projet le 2026-10-05.

* Le trésorier ou l'administrateur crée un **tournoi** : un calendrier de séances
  mensuelles que le serveur crée **automatiquement**.
* **Date** : règle « n-ième jour de semaine du mois » (1er, 2e, 3e, 4e ou dernier
  lundi… dimanche), un passage par mois à partir du mois de départ.
* **Ordre de passage** : liste ordonnée des membres. Le membre de chaque passage
  est **récepteur et bénéficiaire** de sa séance.
* **Lieu** : « Chez <nom du récepteur> » ; **libellé** : « Séance de <mois> <année> ».
  Tout reste modifiable ensuite sur la séance.
* **Cotisations** : une liste par défaut choisie dans le tournoi, rattachée à
  chaque séance générée (sans date limite, décision n° 6), puis ajustable séance
  par séance (décision n° 42). Une cotisation désactivée entre-temps est ignorée.
* **Délai** : chaque séance est créée un nombre de jours **configurable** avant sa
  date, avec **une seule séance à venir à la fois**.
* **Fin** : quand tous les membres de l'ordre ont eu leur séance, le tournoi est
  **terminé** ; aucun nouveau cycle ne démarre seul. Le trésorier crée un nouveau
  tournoi avec un nouvel ordre.

## Règles de cohérence ajoutées

Elles évitent des incohérences techniques et ne fixent aucun montant ni délai :

* **Un seul tournoi en cours** à la fois (sinon deux séances seraient créées
  chaque mois).
* La première séance ne peut pas être déjà passée à la création du tournoi.
* Un tournoi en cours peut changer de nom, de délai et de cotisations par défaut.
  L'**ordre de passage et la règle de date ne changent pas**, pour que les dates
  annoncées restent stables : pour les changer, arrêter le tournoi et en créer un
  nouveau.
* **Arrêter** un tournoi est définitif ; les séances déjà créées sont conservées.
  La **suppression** n'est possible que si aucune séance n'a été générée.
* Une séance générée puis supprimée n'est pas recréée.
* Si le serveur a été arrêté, les passages manqués sont rattrapés au démarrage
  (séances créées à leur date prévue).

## Points à valider

```text
À VALIDER
```

* Si le compte d'un membre de l'ordre est supprimé (décision n° 41), son passage
  disparaît : son mois reste sans séance et les autres membres gardent leur date.
  Faut-il plutôt avancer les passages suivants ?
* Un membre devenu inactif garde son passage dans l'ordre.

## Conséquence technique

* Migration V16 : tables `tournament`, `tournament_slot` (ordre de passage, séance
  générée) et `tournament_contribution`.
* Module backend `tournament/` : `TournamentCalendar` (calcul des dates),
  `TournamentService` (création des séances via `SessionService`, donc mêmes
  contrôles et même audit), `TournamentScheduler` (au démarrage du serveur puis
  chaque jour à 6 h, heure de Douala ; désactivé en test par
  `app.tournament.scheduler.enabled=false`).
* API `/api/tournaments` (ADMIN, TRESORIER) : `GET`, `POST`, `PUT /{id}`,
  `PUT /{id}/stop`, `DELETE /{id}`.
* Audit : `TOURNAMENT_CREATED`, `_UPDATED`, `_STOPPED`, `_COMPLETED`, `_DELETED`,
  `TOURNAMENT_SESSION_GENERATED`. Les séances créées par la tâche planifiée n'ont
  pas d'auteur dans l'audit.
* Interface : page « Tournois » (menu, ADMIN et TRESORIER) avec l'avancement et
  l'ordre de passage ; fenêtre de création avec glisser-déposer de l'ordre et
  aperçu des dates.

---

# 44. Notifications (dans l'application et Web Push)

## Question

De quoi notifier les membres et le personnel, par quel canal, et faut-il des
rappels avant les dates limites ?

## Statut

```text
DÉCIDÉ
```

## Décision

Choix du propriétaire du projet le 2026-10-05.

* **Canaux** : une cloche dans l'application (compteur et liste) **et** de vraies
  notifications **Web Push** sur le téléphone ou l'ordinateur, même application
  fermée, si l'utilisateur les active sur cet appareil.
* **Membres** :
  * nouvelle séance comportant des cotisations auxquelles ils participent, et
    cotisation ajoutée à une séance existante ;
  * paiement, remboursement ou versement d'épargne validé ou rejeté, ainsi que
    remboursement enregistré directement par le trésorier ;
  * prêt approuvé ou refusé ;
  * sanction appliquée ou annulée.
* **Trésorier et administrateur** :
  * paiement, remboursement ou versement d'épargne déclaré (à valider) ;
  * demande de prêt ;
  * séance générée par un tournoi, tournoi terminé.
* **Administrateur seul** : inscription d'un compte à valider.
* **Rappels** : délai **configurable** par le trésorier ou l'administrateur (aucun
  rappel tant qu'il n'est pas fixé). Un rappel **unique** est envoyé aux
  participants qui n'ont pas encore payé, entièrement ou en partie, une cotisation
  **obligatoire** dont la date limite approche. Aucun rappel pour les cotisations
  facultatives (CLAUDE.md §18), ni pour un paiement déjà déclaré et en attente de
  validation.

## Règles ajoutées

* L'auteur d'une action n'est jamais notifié de sa propre action.
* Une notification n'est envoyée en push qu'après l'enregistrement effectif de
  l'opération : une opération refusée ne notifie personne.
* À la **déconnexion**, le push est désactivé sur l'appareil : sur un appareil
  partagé, les notifications d'un membre ne doivent pas arriver chez un autre.
  Il faut le réactiver après chaque connexion.
* Le délai de rappel est limité à 60 jours.

## Points à valider

```text
À VALIDER
```

* Rappels pour les séances sans date limite : aucun rappel n'est envoyé tant que
  la date limite n'est pas fixée (décision n° 6). Faut-il utiliser la date de la
  séance à la place ?
* Le motif d'un rejet de paiement n'existe pas encore : la notification invite à
  contacter le trésorier.

## Conséquence technique

* Migration V17 : tables `notification`, `push_subscription`,
  `notification_settings` (délai des rappels, clés VAPID) et
  `contribution_reminder` (rappels déjà envoyés).
* Module backend `notification/`. Web Push implémenté avec le JDK seul :
  chiffrement RFC 8291 (vérifié avec le vecteur de test officiel) et
  authentification VAPID RFC 8292. Les clés VAPID sont générées au premier
  démarrage et conservées en base, ou fournies par `APP_PUSH_VAPID_PUBLIC_KEY` /
  `APP_PUSH_VAPID_PRIVATE_KEY` ; contact : `APP_PUSH_SUBJECT`.
* Les rappels sont envoyés au démarrage du serveur puis chaque jour à 7 h (heure
  de Douala).
* API `/api/notifications` : liste, compteur des non-lues, marquer comme lu,
  clé publique, abonnement et désabonnement push, test ; `settings` (ADMIN,
  TRESORIER).
* Frontend : cloche dans la barre du haut (rafraîchie toutes les 60 s), page
  « Notifications » (liste, activation du push sur l'appareil, réglage des
  rappels). Le service worker `public/push-sw.js` a la portée `/push/`, pour ne
  pas remplacer celui d'Angular.
* Le push exige HTTPS en production (localhost est accepté en développement). Sur
  iPhone, l'application doit d'abord être ajoutée à l'écran d'accueil.

---

# 45. Informer tous les membres des mouvements des autres membres

## Question

Les membres doivent-ils être informés des mouvements des autres membres
(paiements, prêts, remboursements, épargne, sanctions) ?

## Statut

```text
DÉCIDÉ
```

## Décision

Demande du propriétaire du projet le 2026-10-05 : oui, en complément de la
décision n° 44. Une notification « mouvement d'un membre » (`MEMBER_ACTIVITY`) est
envoyée à tous les autres membres actifs lorsque :

* un paiement de cotisation est **validé** ;
* un versement d'épargne est **validé** ;
* un remboursement de prêt est **validé** ou enregistré par le trésorier ;
* un membre **demande** un prêt, et lorsque ce prêt est **accordé** ou **refusé** ;
* une sanction est **appliquée** ou **annulée**.

Le message indique le nom du membre, le montant et, le cas échéant, la cotisation
et la séance.

## Règles ajoutées

* Seules les opérations **validées** sont annoncées : une déclaration en attente
  ou rejetée n'est pas diffusée (la demande de prêt fait exception, elle est
  annoncée dès son dépôt).
* Le membre concerné ne reçoit pas cette notification (il reçoit déjà la sienne,
  décision n° 44), ni l'auteur de l'action.
* Cotisation **PRIVATE** : seuls ses participants sont informés (CLAUDE.md §11).
* Le **motif** d'un refus de prêt reste réservé au demandeur.
* Pour une demande de prêt, le trésorier et l'administrateur, déjà notifiés
  (`LOAN_REQUESTED`), ne reçoivent pas la notification en double.
* La notification n'a pas de lien : les pages de détail des autres membres sont
  réservées au personnel.
* Seuls les membres actifs dont le compte est actif sont informés.

## Points à valider

```text
À VALIDER
```

* Les montants des prêts, de l'épargne et le détail des sanctions de chaque
  membre deviennent visibles de tous les membres. À confirmer, notamment pour
  l'épargne individuelle et les sanctions.
* Faut-il permettre à un membre de désactiver ces notifications ?

## Conséquence technique

* `NotificationType.MEMBER_ACTIVITY` (backend et frontend, icône « groups »).
* `NotificationService.notifyOtherMembers` et `notifyOtherMembersExceptStaff`,
  appelés depuis `ContributionTransactionService`, `SavingsService`,
  `LoanRepaymentService`, `LoanService` et `AppliedSanctionService`.
* `MemberRepository.findActiveByUserStatus`. Pas de migration : la colonne `type`
  de `notification` est déjà un texte.

---

# 46. Origine de l'argent prêté et remise du fonds commun au bénéficiaire

## Question

D'où vient l'argent prêté, et que devient l'argent des cotisations versées au
fonds commun ?

## Statut

```text
DÉCIDÉ
```

## Décision

Précision du propriétaire du projet le 2026-10-05 :

* **Prêts** : l'argent prêté vient de l'**épargne des membres** qui choisissent
  d'épargner (versements libres et cotisations destinées à l'épargne
  individuelle). Cette épargne rapporte des intérêts aux épargnants.
* **Fonds commun** : l'argent des cotisations du fonds commun n'est pas conservé
  comme une caisse. Il est **remis directement au bénéficiaire de la séance**.
* **Cotisations remises au bénéficiaire** : le choix se fait **à la création de
  la séance**, qui définit déjà le ou les bénéficiaires et les cotisations
  rattachées (décisions n° 42 et 43). Le bénéficiaire reçoit l'argent des
  cotisations **obligatoires** du **fonds commun** choisies pour sa séance
  (ex. 10 000 FCFA et Ration 2 000 FCFA). Aucune liste n'est fixée dans le code,
  même pour la cotisation de 50 000 FCFA. Une cotisation destinée à l'épargne
  individuelle (ex. « Banque / Épargne ») rattachée à la séance reste sur
  l'épargne du membre et n'est jamais remise au bénéficiaire.
* **Plafond des prêts** : un prêt ne peut pas être approuvé s'il dépasse l'argent
  d'épargne disponible. L'approbation est **bloquée**, avec un message explicatif.
* **Intérêts** : les intérêts payés par les emprunteurs sont **partagés entre les
  épargnants**.

## Précisions validées le 2026-10-05

* **Argent disponible pour prêter** = capital d'épargne validé − capital des prêts
  approuvés ou en cours + capital déjà remboursé. Les intérêts (d'épargne comme de
  prêts) n'en font **pas** partie.
* **Montant remis au bénéficiaire** = paiements **validés** des cotisations
  obligatoires du fonds commun rattachées à la séance.
* **Plusieurs bénéficiaires** : **parts égales**.

## Points à valider

```text
À VALIDER
```

* **Arrondi des parts égales** : chaque part est arrondie au franc inférieur ;
  le reliquat (ex. 20 000 / 3 → 3 × 6 666, reliquat 2) reste « non réparti » dans
  le fonds commun. Que faire de ce reliquat ?
* **Correction d'une remise** saisie par erreur : aucune annulation n'est prévue
  pour l'instant.
* Les membres (dont le bénéficiaire) ne voient pas encore le détail de la
  cagnotte dans l'application ; le bénéficiaire est notifié de chaque remise.
* **Clé de partage** des intérêts des prêts entre épargnants : au prorata du
  capital épargné ? En tenant compte de la durée ? À quel moment le partage
  a-t-il lieu ?
* Lien avec le taux d'épargne mensuel de la décision n° 39 : le taux reste-t-il
  utilisé, ou les intérêts d'épargne deviennent-ils uniquement la part des
  intérêts des prêts ?
* Les cotisations obligatoires du fonds commun **non** remises au bénéficiaire
  (si la configuration le prévoit) : à quoi servent-elles ? (lié à la décision
  n° 5, usage de la Ration.)

## Règles de cohérence ajoutées

* Une remise ne dépasse jamais la part restante du bénéficiaire.
* Personne n'enregistre une remise à son propre profit (n° 40).
* Une séance ayant donné lieu à une remise ne peut pas être supprimée, et un
  bénéficiaire qui a reçu une remise ne peut pas être retiré de la séance (n° 35).
* La suppression d'un compte (n° 41) efface aussi les remises qu'il a reçues.

## Conséquence technique (réalisée le 2026-10-05)

* Plafond des prêts : `FundService.availableForLending()` ;
  `LoanService.approve` renvoie `409` au-delà, avec l'argent disponible dans le
  message. `GET /api/funds/available-for-lending` (ADMIN, TRESORIER) ; champ
  `availableForLending` du tableau de bord ; montant affiché dans la fenêtre
  d'approbation d'un prêt.
* Migration V18 : table `session_payout`. Module backend `payout/` :
  `GET /api/sessions/{id}/payouts` (collecté, remis, reste, part de chaque
  bénéficiaire, reliquat, historique) et `POST /api/sessions/{id}/payouts`
  (ADMIN, TRESORIER). Audit `SESSION_PAYOUT_RECORDED` ; notification
  `SESSION_PAYOUT_RECEIVED` au bénéficiaire.
* `FundService.tontineFundBalance()` = collecté − remis ; le tableau de bord
  affiche aussi le total remis.
* Interface : section « Cagnotte du bénéficiaire » sur la page d'une séance
  (personnel) et fenêtre « Remettre la cagnotte ».
* Jeu de démonstration : 400 000 FCFA d'épargne fictive validée, pour que
  l'approbation du prêt de 300 000 FCFA (CLAUDE.md §33) reste possible.

---

# 47. Connexion par code SMS (OTP) en plus de email + mot de passe

## Question

Comment se connecter à l'application : uniquement par email et mot de passe, ou
aussi avec le numéro de téléphone ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-13 :

* Deux façons de se connecter : **email + mot de passe** (inchangé), ou
  **numéro de téléphone + code reçu par SMS** (OTP).
* Cette décision fait entrer l'envoi de **SMS** dans le périmètre, alors que
  CLAUDE.md §3 le classait hors du MVP. Seul l'envoi des codes de connexion est
  concerné : les notifications par SMS restent hors périmètre (décision n° 44).
* Fournisseur SMS : **Igor SMS Gateway** (https://sms.igortek.com/fr/docs).
* Le numéro de connexion est porté par le **compte** : ADMIN, TRESORIER et
  MEMBRE peuvent tous se connecter par code SMS.
* Numéros acceptés : **Cameroun uniquement** (+237 suivi de 9 chiffres
  commençant par 6 ou 2). L'utilisateur peut taper le numéro local ; il est
  enregistré au format `+237XXXXXXXXX`.
* Paramètres retenus (tous configurables, `app.otp`) : code de **6 chiffres**,
  valable **5 minutes**, **5 essais** au maximum, **60 secondes** avant de
  pouvoir redemander un code, **5 demandes par heure** et par numéro.
* À l'auto-inscription, le téléphone reste **facultatif**. Email et mot de
  passe restent obligatoires.

## Règles ajoutées

* Un numéro n'appartient qu'à un seul compte.
* Le téléphone de la fiche membre et le numéro de connexion du compte restent
  identiques : modifier l'un modifie l'autre.
* Seuls les comptes **actifs** reçoivent un code. La réponse est la même que le
  numéro soit inscrit ou non, pour ne pas révéler quels numéros ont un compte.
* Seul le hash du code est conservé. Un code ne sert qu'une fois ; un nouveau
  code annule le précédent.
* Chaque connexion par code SMS est inscrite dans le journal d'audit.

## Points à valider

```text
À VALIDER
```

* **Texte du SMS** proposé : « AEKD Tontine : votre code de connexion est
  123456. Il expire dans 5 min. Ne le communiquez a personne. » (sans accents,
  pour qu'un SMS ne compte que pour un seul message).
* **Formule Igor SMS** (BYOD avec un téléphone passerelle, ou Cloud Pool) et
  budget mensuel des SMS.
* Un membre peut-il **modifier lui-même** son numéro ? Pour l'instant, seul un
  administrateur le peut (écrans Utilisateurs et Membres).
* Un compte **sans mot de passe** (connexion uniquement par SMS) est-il
  souhaitable ? Pour l'instant, un mot de passe reste obligatoire.
* Les numéros déjà saisis sur les fiches membres qui ne sont pas au format
  camerounais n'ont pas été repris comme numéro de connexion : ils sont à
  corriger à la main.

## Conséquence technique (réalisée le 2026-10-13)

* Migration V19 : colonne `users.phone` (unique, format vérifié), reprise des
  numéros valides des fiches membres, table `otp_challenge`.
* `POST /api/auth/otp/request` `{ phone }` puis `POST /api/auth/otp/verify`
  `{ phone, code }` : renvoie le même jeton que `POST /api/auth/login`.
  Erreurs : 400 numéro invalide, 401 code faux ou expiré (avec les essais
  restants), 429 délai ou limite horaire, 503 échec d'envoi du SMS.
* Abstraction `SmsSender` (même principe que `PaymentService`) :
  * `LogSmsSender` (`APP_SMS_PROVIDER=log`, par défaut) : aucun SMS réel, le
    code s'affiche dans le journal du backend (développement, démonstration) ;
  * `IgortekSmsSender` (`APP_SMS_PROVIDER=igortek`) : clé API uniquement par la
    variable d'environnement `IGORTEK_SMS_API_KEY`.
* Interface : page de connexion à deux onglets (Email / Téléphone), saisie du
  code avec « Renvoyer le code » et « Changer de numéro » ; numéro de connexion
  dans la création et la modification d'un compte.
* Jeu de démonstration : numéros fictifs `+237600000001` (admin) à
  `+237600000007`.

---

# 48. Plusieurs fournisseurs SMS, choisis depuis l'application

## Question

Que faire quand le fournisseur SMS ne délivre plus les codes de connexion ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-13, après un premier essai réel
où Igor SMS a accepté le message sans jamais l'envoyer (resté « en file
d'attente ») :

* Deux fournisseurs réels : **Igor SMS Gateway** et **SCMC Host**
  (https://sms.scmchost.com/docs/), plus la **simulation** (code écrit dans le
  journal du serveur).
* Le fournisseur actif se choisit dans un écran **« Paramètres SMS »**, réservé
  à l'**ADMIN**, avec effet immédiat, sans redémarrer le serveur.
* **Secours automatique** (activable dans le même écran) : si le fournisseur
  actif refuse l'envoi ou ne répond pas, le code part par l'autre fournisseur
  réel configuré.
* Un bouton **« Envoyer un SMS de test »** vérifie qu'un fournisseur délivre
  bien, sans secours.

## Règles ajoutées

* Les clés API ne sont jamais saisies ni affichées dans l'application : elles
  restent sur le serveur, dans des fichiers `.env` ignorés par Git
  (`backend/igor-sms-gateway.env`, `backend/scmchost.env`) ou des variables
  d'environnement.
* Un fournisseur sans clé ne peut pas être choisi.
* La simulation n'est jamais utilisée comme secours : un code écrit dans le
  journal n'atteindrait pas l'utilisateur.
* Limite connue : un SMS **accepté** par le fournisseur puis bloqué chez lui
  n'est pas détecté. La bascule manuelle reste alors nécessaire.
* Le changement de fournisseur et chaque SMS de test sont inscrits dans le
  journal d'audit.

## Points à valider

```text
À VALIDER
```

* SCMC Host n'envoie que des **modèles approuvés** : le modèle utilisé pour les
  codes (`SCMCHOST_OTP_TEMPLATE_ID`) et le nom de sa variable (`CODE` par
  défaut) sont à confirmer avec le compte SCMC Host.
* Ordre de priorité si d'autres fournisseurs sont ajoutés plus tard.

## Conséquence technique (réalisée le 2026-10-13)

* `SmsProvider` (un par fournisseur : `LogSmsProvider`, `IgortekSmsProvider`,
  `ScmcHostSmsProvider`) et `SmsRouter`, qui choisit le fournisseur actif et
  applique le secours.
* Migration V20 : table `sms_settings` (ligne unique) ; tant qu'aucun choix
  n'est enregistré, `app.sms.provider` s'applique.
* `GET / PUT /api/settings/sms`, `POST /api/settings/sms/test` (ADMIN).
* Interface : page « Paramètres SMS » dans le menu de l'administrateur.

---

# 49. Qui configure les politiques de prêt ?

## Question

CLAUDE.md se contredit : le §6 range « configurer les règles de prêt » parmi
les droits de l'ADMIN seulement, le §20 indique que les règles de prêt sont
« définies par le trésorier ou l'administrateur ».

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-15 : le **trésorier et
l'administrateur** créent, modifient, activent, désactivent et suppriment les
politiques de prêt (§20). Les membres les consultent.

## Conséquence technique

* `/api/loan-policies` (POST, PUT, DELETE) : rôle TRESORIER ; l'ADMIN y accède
  par la hiérarchie des rôles (décision n° 40).
* Écran « Politiques de prêt » : boutons de gestion visibles pour le trésorier.
* Chaque modification reste inscrite au journal d'audit.

---

# 50. Comptes de l'association

## Question

Comment suivre séparément l'argent des sanctions, du fond de caisse et de la
Ration, à côté du fonds commun et de l'épargne ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-15. L'association a cinq
**comptes**, jamais mélangés (CLAUDE.md §17) : **Fonds commun**, **Épargne et
prêts**, **Sanctions**, **Fond de caisse** et **Ration**.

* **Ration** : alimentée par les cotisations dont la destination est « Ration ».
  Elle reste **remise au bénéficiaire** de la séance avec le fonds commun
  (décision n° 46). Chaque remise sort du fonds commun et de la Ration **au
  prorata** de ce que la séance a collecté pour chacun (ex. 60 000 de fonds
  commun et 2 000 de Ration : une remise de 62 000 sort 60 000 et 2 000).
  Les cotisations nommées « Ration » existantes passent à ce compte, paiements
  déjà validés compris.
* **Fond de caisse** : alimenté par les cotisations dont la destination est
  « Fond de caisse ».
* **Sanctions** : alimenté par le paiement des sanctions monétaires. Le membre
  déclare son paiement, éventuellement en plusieurs fois (opérateur,
  référence, justificatif) ; le trésorier valide ou rejette. Seuls les
  paiements validés entrent au compte ; la sanction est payée quand le total
  validé atteint son montant.
* **Dépenses et virements** : le trésorier enregistre des dépenses (montant,
  motif, date, justificatif) et des virements entre comptes, uniquement sur les
  comptes **Sanctions, Fond de caisse et Ration**. Le fonds commun (remis aux
  bénéficiaires) et l'épargne (argent personnel des membres) restent protégés.
  Un compte ne peut pas passer en négatif. Tout est tracé dans l'audit.

## Points à valider

```text
À VALIDER
```

* Usage précis de la Ration et du fond de caisse (catégories de dépenses).
* Une sanction en nature n'apporte pas d'argent : elle reste hors des comptes.
* Virements : pour l'instant seulement **entre** Sanctions, Fond de caisse et
  Ration (un virement vers le fonds commun ou l'épargne est refusé). Faut-il
  autoriser un virement **vers** le fonds commun ?
* Dépenser la Ration **avant** la remise de la séance : la remise suit le
  prorata de ce qui a été collecté et peut alors rendre le compte Ration
  négatif. Faut-il bloquer les dépenses de Ration tant que la séance n'est pas
  remise, ou ne remettre que ce qui reste ?
* Une dépense ou un virement est **définitif** (pas d'annulation) ; une erreur
  se corrige par une opération inverse. À confirmer.
* Justificatif des dépenses : pas encore implémenté (montant, motif, date et
  observation uniquement).

## Conséquence technique

* Destinations de cotisation `RATION` et `CASH_FUND` ; migration V22.
* Remise de cagnotte : cotisations obligatoires du fonds commun et de la
  Ration ; répartition au prorata (`FundService.rationPartOf`).
* Écran « Fonds » : un solde par compte et un journal filtrable par compte.
* Paiement des sanctions : table `sanction_payment` (V23), endpoints
  `/api/sanctions/applied/{id}/payments` et `/api/sanctions/payments/...` ;
  une sanction qui a des paiements ne peut plus être annulée.
* Dépenses et virements : table `account_operation` (V24), endpoints
  `GET/POST /api/funds/operations` (enregistrement TRESORIER), contrôle de
  solde sous verrou PostgreSQL (`pg_advisory_xact_lock`).

---

# 51. Photo de profil et menu par thèmes

## Question

Comment rendre l'interface plus personnelle et le menu plus lisible ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-15.

* Chaque utilisateur peut ajouter, changer ou retirer **sa** photo de profil
  (page « Mon profil »). Personne ne peut modifier la photo d'un autre.
* Les photos sont **visibles par tous les utilisateurs connectés** (liste des
  membres, détail d'une séance, participants d'une cotisation, barre du haut).
* Le menu latéral est regroupé par thèmes repliables : Mon espace, Tontine,
  Finances, Administration, Paramètres.

## Conséquence technique

* Colonnes `photo_storage_key` et `photo_content_type` sur `users` (V25) ;
  fichier sur le stockage (`profile-photos/`), effacé avec le compte.
* `GET/POST/DELETE /api/profile/photo` (sa photo) et
  `GET /api/members/{memberId}/photo` (tout utilisateur connecté).
* L'image est recadrée en carré 320 px (JPEG) dans le navigateur ; le serveur
  n'accepte que JPG, PNG ou WEBP de 2 Mo au plus.

---

# 52. Avaliste d'un prêt

## Question

Comment garantir un prêt si le membre emprunteur ne rembourse pas ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-15.

* À la demande de prêt, le membre désigne un **avaliste** : un autre membre
  actif, garant si le prêt n'est pas remboursé.
* La politique de prêt indique si l'avaliste est **obligatoire** (case
  « Avaliste obligatoire », réglée par le trésorier ou l'administrateur).
* L'avaliste **accepte ou refuse**. Le trésorier ne peut approuver le prêt
  qu'après son accord. En cas de refus, le membre en désigne un autre.
* L'avaliste doit être **à jour**. Règle « Avaliste à jour », **signalée au
  trésorier** comme les autres règles de prêt (elle ne bloque pas la demande) :
  * aucune cotisation obligatoire échue et non soldée (en retard, ou payée en
    partie après l'échéance) ;
  * aucune sanction monétaire appliquée qui reste à payer (seuls les paiements
    validés comptent) ;
  * aucun prêt en cours dont la durée approuvée est dépassée.

## Points à valider

```text
À VALIDER
```

* Ce qui se passe concrètement si l'emprunteur ne rembourse pas : rien n'est
  automatisé (pas de prélèvement sur l'épargne de l'avaliste, par exemple).
* Un même membre peut-il garantir plusieurs prêts à la fois ? Aujourd'hui, oui.
* La règle « à jour » est évaluée à la demande (et à chaque changement
  d'avaliste), pas recalculée au moment de l'approbation.

## Conséquence technique

* `loan_policy.guarantor_required` ; `loan.guarantor_member_id`,
  `guarantor_status` (PENDING, ACCEPTED, DECLINED), `guarantor_decided_at` (V26).
* `GuarantorStandingService` calcule ce qui empêche un membre d'être à jour.
* Endpoints : `GET /api/loans/guarantor-candidates`, `GET /api/loans/guaranteed`,
  `PUT /api/loans/{id}/guarantee/accept|decline`, `PUT /api/loans/{id}/guarantor`.
* L'avaliste peut consulter le prêt qu'il garantit.

---

# 53. Période du taux, limite d'aval et recouvrement des prêts

## Question

Comment exprimer un taux qui ne serait pas mensuel, limiter les avals d'un
même membre, et récupérer l'argent d'un prêt non remboursé ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-15.

* **Période du taux** : la politique de prêt indique la période à laquelle
  s'applique le taux (mensuel, bimestriel, trimestriel, semestriel, annuel).
  Les intérêts sont calculés **au prorata** de la durée : 5 % tous les 2 mois
  sur 2 mois donnent 5 %, sur 3 mois 7,5 %. Exemple du président : un prêt de
  deux mois à 5 % bimestriel coûte 5 %. La période est figée sur le prêt à
  l'approbation, comme le taux (n° 14).
* **Limite d'aval** : la politique peut fixer le nombre maximum de prêts en
  cours qu'un même membre garantit. Un dépassement est **signalé au trésorier**
  dans la règle « Avaliste à jour » ; il ne bloque pas.
* **Vérification à l'approbation** : la règle « Avaliste à jour » est
  recalculée au moment où le trésorier approuve.
* **Recouvrement d'un prêt non remboursé** :
  * à la **remise de cagnotte** d'un membre qui a un prêt en cours, le reste
    à rembourser est **déduit** de sa cagnotte ;
  * à la remise de cagnotte de son **avaliste**, le reste est aussi déduit,
    mais **l'emprunteur d'abord** : seulement si l'épargne de l'emprunteur
    est épuisée ;
  * le trésorier peut aussi **prélever sur l'épargne** de l'emprunteur, puis
    sur celle de l'avaliste si l'épargne de l'emprunteur est épuisée.
* Précisions validées le 2026-10-15 :
  * la retenue sur la cagnotte est **préremplie** ; le trésorier peut la
    réduire ou la retirer ;
  * le prélèvement sur l'**épargne** n'est possible que pour un prêt **en
    retard** (durée approuvée dépassée) ; la retenue sur la **cagnotte** est
    possible dès que le prêt court ;
  * « épargne de l'emprunteur épuisée » = ses versements d'épargne validés
    sont à zéro ; ses intérêts d'épargne ne sont pas prélevés.

## Points à valider

```text
À VALIDER
```

* Une retenue ou un prélèvement est définitif (comme un remboursement validé).

## Conséquence technique

* `loan_policy.interest_period_months`, `loan_policy.max_guarantees_per_member`,
  `loan.interest_period_months` (V27) ; `LoanInterest.interest(capital, taux, mois, période)`.
* Une retenue ou un prélèvement est un `LoanRepayment` VALIDATED avec
  `source` (PAYOUT_DEDUCTION, SAVINGS_DEDUCTION), `payer_member_id` et
  `session_payout_id` ; `session_payout.deducted_amount` (V28). Le solde du
  prêt et son passage à REPAID suivent donc les règles habituelles.
* Épargne d'un membre = versements validés − prélèvements ; les prélèvements
  apparaissent en négatif dans l'historique d'épargne et le relevé PDF.
* Endpoints : `GET /api/sessions/{id}/payouts/deduction-options?memberId=`,
  `POST /api/loans/{id}/savings-deduction` (TRESORIER) ; la remise de cagnotte
  accepte une liste `deductions`.

---

# 54. Session longue sur l'appareil et page « compte en attente »

## Question

Faut-il redemander le code SMS ou le mot de passe à chaque retour sur
l'application ? Que voit un membre inscrit dont le compte n'est pas validé ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-15.

* Un membre connecté reste connecté sur son appareil **30 jours sans
  activité** ; la durée repart à zéro à chaque utilisation. La déconnexion y
  met fin immédiatement. Au-delà, il se reconnecte (code SMS ou mot de passe).
* Un membre inscrit dont le compte n'est pas encore validé arrive sur une page
  « Compte en attente de validation par l'administrateur » : juste après
  l'inscription, et à chaque tentative de connexion (mot de passe juste, ou
  demande de code SMS) tant que l'administrateur n'a pas validé.

## Conséquence technique

* Le jeton d'accès (JWT) reste valable 60 minutes. Un jeton de renouvellement
  aléatoire (table `refresh_token`, V29, empreinte SHA-256 seulement) permet
  d'en obtenir un nouveau en silence ; il est remplacé à chaque utilisation et
  révoqué à la déconnexion. Durée réglable : `APP_JWT_REFRESH_TOKEN_DAYS` (30).
* Endpoints publics `POST /api/auth/refresh` et `POST /api/auth/logout`.
* Un compte DISABLED ne peut plus renouveler sa session.
* Compte en attente : réponse 403 « en attente de validation » (mot de passe
  juste, ou demande de code SMS pour son numéro). Un mauvais mot de passe
  reste un 401 qui ne révèle rien. La demande de code SMS révèle en revanche
  qu'un numéro appartient à un compte en attente (accepté pour l'ergonomie).

---

# 55. Codes par e-mail : confirmation de l'adresse et connexion

## Question

Comment profiter des vraies adresses e-mail des membres, en plus des SMS ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-16.

* **Confirmation de l'adresse** : à l'inscription, un code à 6 chiffres est
  envoyé par e-mail ; le membre le saisit sur la page d'attente. Un membre
  déjà inscrit peut vérifier son adresse depuis « Mon profil ».
* **Connexion par code e-mail** : sur la page de connexion (onglet Email),
  « Recevoir plutôt un code par e-mail ». Seulement pour un compte actif dont
  l'adresse est **vérifiée** ; réponse identique sinon.
* Mêmes règles que les codes SMS (n° 47) : 6 chiffres, 5 minutes, 5 essais,
  60 secondes avant d'en redemander un, 5 demandes par heure et par adresse.
* Envoi par le service **Brevo** (API HTTPS). Tant qu'il n'est pas configuré,
  les e-mails sont simulés : écrits dans les journaux du serveur.

## Points à valider

```text
À VALIDER
```

* La confirmation de l'adresse ne bloque pas la validation du compte par
  l'administrateur : il voit seulement si l'adresse est vérifiée.
* Changer d'adresse e-mail annule sa vérification.

## Conséquence technique

* `users.email_verified_at` et table `email_code` (V30, hash du code seulement).
* Variables : `APP_EMAIL_PROVIDER` (`log` ou `brevo`), `BREVO_API_KEY`,
  `APP_EMAIL_FROM_ADDRESS` (expéditeur validé dans Brevo), `APP_EMAIL_FROM_NAME`.
* Endpoints publics : `POST /api/auth/email/verification`, `/email/verify`,
  `/email-otp/request`, `/email-otp/verify` ; profil :
  `GET /api/profile/email`, `POST /api/profile/email/code`, `/verify`.

---

# 56. Super administrateur et paramètres du système

## Question

Qui règle les paramètres techniques de l'application (SMS, e-mail…) et gère
les administrateurs ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-16.

* Nouveau rôle **SUPER_ADMIN** : tous les droits de l'administrateur, plus les
  paramètres du système. Le compte **admin@aekd.cm** devient super
  administrateur.
* Réservé au super administrateur :
  * **Paramètres SMS** (fournisseur, secours, SMS de test) ;
  * **Paramètres e-mail** (nouvel écran : état de Brevo, envoi réel ou
    simulation, e-mail de test) ;
  * **Rôles administrateurs** : seul lui nomme ou retire un administrateur ou
    un super administrateur, et modifie un compte administrateur ;
  * **Suppression définitive** d'un compte (l'administrateur peut toujours le
    désactiver).
* L'application garde toujours au moins un super administrateur actif.

## Points à valider

```text
À VALIDER
```

D'autres réglages pourront rejoindre « Paramètres » (le propriétaire souhaite
en ajouter). Propositions, non implémentées :

* durée de la session sur l'appareil (aujourd'hui 30 jours, n° 54) ;
* règles des codes SMS / e-mail (durée, essais, délai de renvoi, n° 47) ;
* informations de l'association (nom, logo, coordonnées sur les PDF) ;
* mode maintenance (bloquer les connexions sauf le bureau).

## Conséquence technique

* Rôle `SUPER_ADMIN` (V31), hiérarchie SUPER_ADMIN > ADMIN > TRESORIER. Un
  super administrateur garde aussi le rôle ADMIN (les notifications et règles
  existantes visent ADMIN).
* Table `email_settings` (V31) ; `GET/PUT /api/settings/email`,
  `POST /api/settings/email/test`. La clé Brevo reste dans les variables du
  serveur et n'est jamais renvoyée.

---

# 57. Paramètres système

## Question

Quels réglages le super administrateur peut-il modifier sans toucher au serveur ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-16. Écran « Paramètres système »
(super administrateur) :

* **Association** : nom, sigle, adresse, téléphone, e-mail et logo, affichés sur
  les documents PDF (en-tête et pied de page).
* **Durée de session** sur l'appareil (n° 54) et **règles des codes** SMS et
  e-mail (n° 47 et 55) : validité, essais, délai avant un nouveau code, codes par
  heure. Une case vide reprend la valeur par défaut du serveur.
* **Mode maintenance** : seul le **super administrateur** peut se connecter et
  utiliser l'application ; les autres voient le message choisi (aussi sur la page
  de connexion).

## Conséquence technique

* Table `system_settings` (V32), gardée en mémoire et relue à chaque modification.
* `GET/PUT /api/settings/system`, `POST/DELETE /api/settings/system/logo`
  (SUPER_ADMIN) ; `GET /api/public/info` (public : nom, maintenance).
* Maintenance : refus à l'émission de tout jeton (mot de passe, codes,
  renouvellement) et réponse 503 `maintenance: true` sur l'API pour toute
  session autre que celle du super administrateur.

---

# 58. Notifications par e-mail et SMS

## Question

Faut-il prévenir les membres par e-mail et SMS, en plus de la notification dans
l'application, et pour quelles actions ?

## Statut

```text
DÉCIDÉ
```

## Décision

Validé par le propriétaire du projet le 2026-10-16.

* Écran « Notifications e-mail et SMS » (super administrateur) : pour chaque type
  de notification (paiement validé, prêt approuvé, sanction appliquée, demande
  de prêt…), l'**e-mail** et le **SMS** s'activent **séparément**. Tout est
  désactivé au départ. La notification dans l'application est toujours envoyée.
* Réglage **global** : les membres ne peuvent pas refuser ces envois.
* E-mail : seulement vers une adresse **vérifiée**. SMS : vers le numéro du compte.
* Les envois partent en arrière-plan après l'action ; un échec n'annule jamais
  l'action.

## Points à valider

```text
À VALIDER
```

* SCMC Host n'envoie que des **modèles approuvés** : il faut lui faire valider
  un modèle « notification » avec une variable de texte (ex. {MESSAGE}), puis
  renseigner `SCMCHOST_NOTIFY_TEMPLATE_ID`. Igor SMS envoie du texte libre.
* Un SMS est limité à 300 caractères (texte tronqué au-delà).

## Conséquence technique

* Table `notification_channel` (V33) ; `GET/PUT /api/settings/notifications`.
* `ChannelDispatcher` écoute la création des notifications (après validation de
  la transaction, en tâche de fond) ; `SmsSender.sendText` pour le texte libre.
* `APP_PUBLIC_URL` : adresse de l'application, pour le lien « Ouvrir dans
  l'application » des e-mails.
