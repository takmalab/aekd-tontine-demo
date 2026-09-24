# AEKD – Gestion de Tontine

## 1. CONTEXTE DU PROJET

AEKD-Tontine est une application web de gestion de la tontine de l’**Amicale des Enfants Kamkop de Douala (AEKD)**.

L'objectif est de développer d'abord un **MVP web fonctionnel, démontrable et propre**, avant d'envisager une version complète destinée à la production.

L'application doit permettre de gérer :

* les membres ;
* les comptes utilisateurs ;
* les séances mensuelles ;
* les cotisations ;
* les participants aux cotisations ;
* les bénéficiaires ;
* les paiements et leurs justificatifs ;
* les sanctions ;
* l'épargne individuelle ;
* les fonds communs de la tontine ;
* les prêts ;
* les remboursements ;
* les tableaux de bord ;
* la traçabilité des opérations.

---

# 2. PRINCIPES IMPORTANTS

Claude Code doit respecter les principes suivants :

1. **Ne jamais inventer une règle métier qui n'a pas été définie.**
2. Lorsqu'une règle métier est inconnue ou ambiguë, l'identifier clairement comme une **décision à valider**.
3. Ne pas fusionner des cotisations différentes simplement parce qu'elles sont dues pendant la même séance.
4. Séparer clairement :

   * la définition/configuration d'une cotisation ;
   * la période d'application de cette cotisation ;
   * la déclaration de paiement ;
   * la validation du paiement.
5. Séparer le **fonds commun de la tontine** de l'**épargne individuelle**.
6. Les règles de prêt doivent être configurables et non codées en dur.
7. Les sanctions configurées doivent être séparées des sanctions réellement appliquées.
8. Le MVP doit rester simple et démontrable.
9. Toute fonctionnalité future doit pouvoir être ajoutée sans devoir réécrire complètement l'architecture existante.
10. Ne pas exposer directement les entités JPA dans les API : utiliser des DTO.

---

# 3. PÉRIMÈTRE DU MVP

## Inclus dans le MVP

* Authentification
* Gestion des utilisateurs
* Gestion des membres
* Gestion des rôles
* Gestion des séances
* Création/configuration des cotisations
* Gestion des participants
* Gestion des bénéficiaires
* Gestion des sanctions
* Déclaration manuelle des paiements
* Téléversement de justificatifs
* Validation/rejet des paiements
* Gestion du fonds commun
* Gestion de l'épargne individuelle
* Configuration des règles de prêt
* Demande de prêt
* Validation/rejet d'un prêt
* Remboursement de prêt
* Tableaux de bord
* Journal d'audit
* Interface web responsive

## Hors périmètre du MVP

Ne pas implémenter pour le moment :

* application mobile Flutter ;
* intégration réelle MTN Mobile Money ;
* intégration réelle Orange Money ;
* intégration bancaire ;
* notifications WhatsApp ;
* SMS ;
* notifications push ;
* export Excel ;
* export PDF ;
* gestion avancée des réunions ;
* gestion de plusieurs tontines ;
* comptabilité complète ;
* système complexe de permissions.

Ces fonctionnalités pourront être ajoutées ultérieurement.

---

# 4. TECHNOLOGIES

## Backend

Utiliser :

* Java 21
* Spring Boot 3.x
* Spring Web
* Spring Data JPA
* Spring Security
* JWT
* PostgreSQL
* Flyway
* Bean Validation
* Maven
* OpenAPI / Swagger

L'architecture backend doit suivre :

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Utiliser des DTO pour les entrées/sorties API.

## Frontend

Utiliser :

* Angular
* TypeScript
* Angular Material
* RxJS
* Angular Router
* Reactive Forms
* HTTP Interceptors
* Route Guards

## Base de données

PostgreSQL.

Docker Compose peut être utilisé pour faciliter l'environnement de développement.

---

# 5. ARCHITECTURE BACKEND

Organiser le backend avec des modules clairs.

Structure recommandée :

```text
backend/
└── src/main/java/...
    ├── auth/
    ├── user/
    ├── member/
    ├── session/
    ├── contribution/
    ├── contributionrule/
    ├── sanction/
    ├── loan/
    ├── repayment/
    ├── dashboard/
    ├── audit/
    ├── security/
    ├── config/
    └── common/
```

Chaque module doit rester responsable de son domaine.

---

# 6. RÔLES

L'application possède trois rôles principaux :

```text
ADMIN
TRESORIER
MEMBRE
```

## ADMIN

L'administrateur peut notamment :

* gérer les utilisateurs ;
* gérer les membres ;
* créer et gérer les séances ;
* créer/configurer les cotisations ;
* gérer les participants ;
* gérer les bénéficiaires ;
* configurer les sanctions ;
* configurer les règles de prêt ;
* consulter les paiements ;
* consulter les fonds ;
* consulter les prêts ;
* consulter les audits.

## TRESORIER

Le trésorier peut notamment :

* gérer les séances opérationnelles ;
* créer/configurer les cotisations ;
* gérer les participants ;
* gérer les bénéficiaires ;
* configurer les sanctions ;
* consulter les paiements ;
* valider/rejeter les paiements ;
* gérer les fonds ;
* traiter les demandes de prêt ;
* valider/rejeter les prêts ;
* enregistrer les remboursements.

## MEMBRE

Un membre peut :

* consulter son tableau de bord ;
* consulter les cotisations auxquelles il participe ;
* déclarer un paiement ;
* transmettre un justificatif ;
* consulter l'état de ses paiements ;
* consulter ses sanctions ;
* consulter son épargne individuelle ;
* demander un prêt ;
* consulter ses prêts ;
* enregistrer/consulter ses remboursements.

---

# 7. CONCEPT DE SÉANCE

La séance est un concept central du système.

Une séance représente une période de rencontre/gestion de la tontine.

Exemple :

```text
Séance de septembre 2026
Séance d'octobre 2026
Séance de novembre 2026
```

Une séance peut contenir plusieurs cotisations.

Les cotisations doivent donc être rattachées à une séance ou être applicables à une séance via un mécanisme approprié.

Le système doit permettre de suivre les contributions **par membre, par cotisation et par séance**.

---

# 8. COTISATIONS

Une cotisation n'est pas simplement un paiement.

Une cotisation est une **définition métier/configuration**.

Elle doit pouvoir contenir notamment :

* nom ;
* description ;
* montant ;
* fréquence ;
* mode de montant ;
* obligatoire ou facultative ;
* visibilité publique ou privée ;
* participants ;
* bénéficiaires ;
* règles de sanction ;
* destination des fonds ;
* statut.

## Types de montant

Prévoir au minimum :

```text
FIXED
VOLUNTARY
```

`FIXED` signifie que le montant est défini.

`VOLUNTARY` signifie que le membre peut saisir le montant à verser selon les règles de la cotisation.

---

# 9. COTISATIONS ACTUELLES AEKD

Les cotisations connues pour le projet sont :

## 1. Cotisation 50 000 FCFA

* Montant : 50 000 FCFA
* Fréquence : mensuelle / par séance
* Obligatoire : oui
* Destination : fonds commun de la tontine

## 2. Cotisation 10 000 FCFA

* Montant : 10 000 FCFA
* Fréquence : mensuelle / par séance
* Obligatoire : oui
* Destination : fonds commun de la tontine

## 3. Ration

* Montant : 2 000 FCFA
* Fréquence : mensuelle / par séance
* Obligatoire : oui
* Destination : fonds commun de la tontine

## 4. Voir bébé

* Fréquence : mensuelle / par séance
* Obligatoire : non
* Montant : à définir
* Destination : à confirmer

## 5. Banque / Épargne

* Fréquence : mensuelle / par séance
* Obligatoire : non
* Montant : à définir/configurable
* Destination : épargne individuelle du membre

### IMPORTANT

Les cotisations de 50 000 FCFA, 10 000 FCFA et Ration ne doivent **pas** être fusionnées en une cotisation de 62 000 FCFA.

Elles restent trois cotisations distinctes.

Le montant obligatoire actuellement connu par séance est donc :

```text
50 000 + 10 000 + 2 000 = 62 000 FCFA
```

hors cotisations facultatives.

---

# 10. PARTICIPANTS

Une cotisation peut concerner :

* tous les membres ;
* certains membres uniquement.

Le trésorier ou l'administrateur doit pouvoir sélectionner les participants.

Un membre ne doit pouvoir cotiser (déclarer un paiement pour une cotisation) que s'il est participant à cette cotisation.

---

# 11. VISIBILITÉ

Une cotisation peut être :

```text
PUBLIC
PRIVATE
```

## PUBLIC

Tous les membres peuvent voir l'existence de la cotisation.

Cela ne signifie pas automatiquement qu'ils sont participants.

## PRIVATE

La cotisation est visible uniquement par :

* ADMIN ;
* TRESORIER ;
* membres sélectionnés comme participants.

---

# 12. BÉNÉFICIAIRES

Les bénéficiaires sont indépendants des participants.

Une cotisation peut avoir :

* aucun bénéficiaire ;
* un bénéficiaire ;
* plusieurs bénéficiaires.

Ne pas supposer qu'un participant est automatiquement bénéficiaire.

---

# 13. PÉRIODE DE CONTRIBUTION

Il faut distinguer :

```text
ContributionDefinition
```

et

```text
ContributionPeriod
```

La définition décrit la cotisation.

La période permet de suivre son application pour une séance donnée.

Le système doit pouvoir déterminer pour chaque membre :

```text
PAID
PENDING
LATE
NOT_PAID
```

selon la situation.

---

# 14. PAIEMENTS

Une déclaration de paiement doit notamment contenir :

* membre ;
* cotisation ;
* séance/période ;
* montant ;
* opérateur ;
* référence de transaction ;
* date de paiement ;
* observation éventuelle ;
* justificatif éventuel ;
* statut.

Statuts :

```text
PENDING
VALIDATED
REJECTED
```

Un paiement déclaré par un membre n'est pas automatiquement considéré comme encaissé.

Seul un paiement validé doit être pris en compte dans les fonds.

---

# 15. OPÉRATEURS DE PAIEMENT

Prévoir au minimum :

```text
MTN_MOMO
ORANGE_MONEY
BANK_TRANSFER
CASH
```

Dans le MVP, il n'y a **aucune intégration réelle avec les API de paiement**.

Le membre déclare manuellement son paiement.

L'architecture doit néanmoins prévoir une abstraction permettant plus tard d'intégrer les vrais opérateurs.

Exemple :

```text
PaymentService
    └── ManualPaymentService
```

Plus tard :

```text
PaymentService
    ├── ManualPaymentService
    ├── MtnMomoPaymentService
    └── OrangeMoneyPaymentService
```

---

# 16. JUSTIFICATIFS

Un paiement peut avoir un justificatif.

Formats recommandés :

```text
JPG
JPEG
PNG
PDF
```

Pour le MVP :

```text
LocalFileStorageService
```

Prévoir une abstraction :

```text
FileStorageService
```

afin de pouvoir utiliser ultérieurement :

* Amazon S3 ;
* Cloudinary ;
* Google Cloud Storage ;
* autre stockage cloud.

---

# 17. DESTINATION DES FONDS

Le système doit distinguer :

```text
TONTINE_FUND
INDIVIDUAL_SAVINGS
```

## TONTINE_FUND

Fonds commun de la tontine.

Les cotisations connues suivantes y sont destinées :

* 50 000 FCFA ;
* 10 000 FCFA ;
* Ration.

## INDIVIDUAL_SAVINGS

Épargne personnelle du membre.

La cotisation Banque / Épargne est destinée à cette catégorie.

### IMPORTANT

Ne jamais mélanger les montants du fonds commun avec les montants d'épargne individuelle.

---

# 18. SANCTIONS

Une règle de sanction doit être séparée de la sanction réellement appliquée.

Utiliser deux concepts :

```text
SanctionRule
AppliedSanction
```

## SanctionRule

Définit la règle.

Exemple :

```text
Retard supérieur à 3 jours
→ sanction de 2 000 FCFA
```

Types :

```text
MONETARY
IN_KIND
```

Une sanction monétaire possède un montant.

Une sanction en nature possède une description.

## AppliedSanction

Représente la sanction réellement appliquée à un membre.

Elle doit conserver notamment :

* membre ;
* règle ;
* cotisation ;
* séance ;
* date ;
* statut ;
* montant ou description ;
* utilisateur ayant appliqué la sanction.

### IMPORTANT

Une cotisation facultative non payée ne doit pas automatiquement générer une sanction pour non-paiement.

Les seuils exacts et montants des sanctions restent à valider lorsqu'ils ne sont pas définis.

---

# 19. DÉTECTION DES RETARDS

Le système doit pouvoir identifier automatiquement les contributions obligatoires en retard.

Exemple :

```text
Date limite dépassée
+
cotisation obligatoire
+
paiement non validé
=
retard
```

Le système pourra ensuite déclencher ou proposer l'application d'une sanction selon la règle configurée.

Ne jamais inventer les délais ou montants de sanction.

---

# 20. PRÊTS

Les règles de prêt sont définies par le trésorier ou l'administrateur.

Elles ne doivent pas être codées en dur.

Créer un concept :

```text
LoanPolicy
```

Une politique de prêt peut contenir notamment :

* nom ;
* description ;
* montant minimum ;
* montant maximum ;
* durée maximale ;
* taux d'intérêt ;
* épargne minimale ;
* ancienneté minimale ;
* nombre maximum de prêts actifs ;
* statut actif/inactif.

Ces règles doivent rester configurables.

---

# 21. DEMANDE DE PRÊT

Un membre peut demander un prêt avec :

* montant ;
* durée ;
* motif.

Le système doit vérifier les règles de la politique active.

Si une règle n'est pas respectée, le système doit expliquer laquelle.

Le trésorier peut ensuite :

* approuver ;
* rejeter.

Conserver :

* montant demandé ;
* montant approuvé ;
* durée demandée ;
* durée approuvée ;
* date de décision ;
* décideur ;
* motif de rejet éventuel.

---

# 22. STATUTS DES PRÊTS

Prévoir notamment :

```text
REQUESTED
APPROVED
IN_PROGRESS
REJECTED
REPAID
```

Le statut doit évoluer selon les opérations effectuées.

---

# 23. REMBOURSEMENTS

Un remboursement doit contenir notamment :

* prêt ;
* montant ;
* date ;
* opérateur ;
* référence ;
* justificatif éventuel ;
* utilisateur ayant enregistré l'opération.

Le système doit calculer :

```text
Montant total du prêt
-
Montant total remboursé
=
Solde restant
```

Empêcher les remboursements supérieurs au solde restant.

Lorsque le solde atteint zéro :

```text
Loan.status = REPAID
```

---

# 24. ÉPARGNE ET PRÊTS

L'épargne individuelle doit être séparée du fonds commun.

Le modèle doit permettre de déterminer l'épargne individuelle d'un membre à partir des opérations validées.

Les règles précises concernant l'utilisation de l'épargne comme garantie ou condition d'accès au prêt doivent être configurables via `LoanPolicy`.

Ne pas inventer une règle supplémentaire.

---

# 25. TABLEAUX DE BORD

## ADMIN / TRESORIER

Le tableau de bord doit afficher notamment :

* nombre de membres ;
* séance actuelle ;
* cotisations obligatoires attendues ;
* paiements validés ;
* paiements en attente ;
* contributions en retard ;
* montant collecté ;
* fonds commun ;
* épargne individuelle totale ;
* demandes de prêt ;
* prêts en cours ;
* sanctions.

## MEMBRE

Le tableau de bord doit afficher notamment :

* séance actuelle ;
* cotisations obligatoires ;
* cotisations payées ;
* cotisations en attente ;
* cotisations en retard ;
* cotisations facultatives ;
* épargne individuelle ;
* prêts ;
* remboursements ;
* sanctions.

---

# 26. JOURNAL D'AUDIT

Les opérations importantes doivent être traçables.

Créer un `AuditLog`.

Informations recommandées :

* date/heure ;
* utilisateur ;
* action ;
* entité ;
* identifiant de l'entité ;
* description.

Tracer notamment :

* création/modification d'une séance ;
* création/modification/activation d'une cotisation ;
* déclaration d'un paiement ;
* validation d'un paiement ;
* rejet d'un paiement ;
* création/application d'une sanction ;
* demande de prêt ;
* approbation d'un prêt ;
* rejet d'un prêt ;
* remboursement.

---

# 27. MODÈLE DE DONNÉES INITIAL

Le modèle doit au minimum prévoir les concepts suivants :

```text
User
Role
Member
Session
ContributionDefinition
ContributionParticipant
ContributionBeneficiary
ContributionPeriod
ContributionTransaction
PaymentProof
SanctionRule
AppliedSanction
LoanPolicy
Loan
LoanRuleEvaluation
LoanRepayment
AuditLog
```

Relations principales :

```text
User 1 ─── 1 Member

Session 1 ─── N ContributionDefinition

ContributionDefinition 1 ─── N ContributionParticipant

ContributionDefinition 1 ─── N ContributionBeneficiary

ContributionDefinition 1 ─── N SanctionRule

SanctionRule 1 ─── N AppliedSanction

ContributionDefinition 1 ─── N ContributionTransaction

ContributionTransaction ─── PaymentProof

LoanPolicy 1 ─── N Loan

Loan 1 ─── N LoanRepayment
```

Le modèle peut être ajusté pendant la conception technique si cela améliore la cohérence, mais toute modification importante doit être documentée.

---

# 28. API PRINCIPALES

Prévoir notamment :

```text
POST /api/auth/login

GET  /api/sessions
POST /api/sessions
GET  /api/sessions/{id}

GET    /api/contributions
POST   /api/contributions
PUT    /api/contributions/{id}
DELETE /api/contributions/{id}

POST /api/contributions/{id}/participants
POST /api/contributions/{id}/beneficiaries

POST /api/contributions/{id}/payments

GET /api/contributions/payments/pending

PUT /api/contributions/payments/{id}/validate
PUT /api/contributions/payments/{id}/reject

GET  /api/sanctions
POST /api/sanctions

GET  /api/loan-policies
POST /api/loan-policies

GET  /api/loans
POST /api/loans
PUT  /api/loans/{id}/approve
PUT  /api/loans/{id}/reject

GET  /api/loans/{id}/repayments
POST /api/loans/{id}/repayments

GET /api/dashboard
GET /api/audit
```

Les endpoints exacts pourront être adaptés pendant l'implémentation.

---

# 29. STRUCTURE FRONTEND

Organisation recommandée :

```text
frontend/
└── src/app/
    ├── core/
    ├── shared/
    ├── layout/
    └── features/
        ├── auth/
        ├── dashboard/
        ├── members/
        ├── sessions/
        ├── contributions/
        ├── sanctions/
        ├── loans/
        ├── repayments/
        ├── cash/
        └── audit/
```

Utiliser :

* services ;
* guards ;
* interceptors ;
* interfaces/types ;
* reactive forms ;
* composants réutilisables.

---

# 30. UX

L'application doit avoir une interface :

* moderne ;
* professionnelle ;
* responsive ;
* claire ;
* adaptée à un usage administratif.

Prévoir notamment :

* sidebar ;
* topbar ;
* cartes statistiques ;
* tableaux ;
* formulaires ;
* fenêtres de dialogue ;
* notifications ;
* confirmations avant actions sensibles.

La page d'une séance doit clairement afficher les différentes cotisations séparément.

---

# 31. CRÉATION D'UNE COTISATION

L'interface peut utiliser un assistant en plusieurs étapes :

```text
1. Informations générales
2. Montant / fréquence
3. Obligatoire ou facultative
4. Visibilité
5. Participants
6. Bénéficiaires
7. Sanctions
8. Destination des fonds
9. Récapitulatif
10. Activation
```

Le système doit permettre de revoir les informations avant activation.

---

# 32. DONNÉES DE DÉMONSTRATION

Créer des données fictives pour la démonstration.

Ne pas utiliser le nom réel du propriétaire du projet comme membre de démonstration.

Créer notamment :

* 1 administrateur ;
* 1 trésorier ;
* au moins 5 membres fictifs ;
* une séance de démonstration ;
* les cotisations connues ;
* quelques paiements ;
* une règle de sanction ;
* une politique de prêt ;
* quelques demandes de prêt.

Comptes de démonstration proposés :

```text
admin@aekd.cm
Admin@123

tresorier@aekd.cm
Tresorier@123
```

Ces identifiants sont uniquement destinés au développement/démonstration.

---

# 33. FLUX DE DÉMONSTRATION PRINCIPAL

Le MVP doit permettre de démontrer le scénario suivant :

### Étape 1

Le trésorier se connecte.

### Étape 2

Il crée une séance :

```text
Octobre 2026
```

### Étape 3

Il crée une cotisation :

```text
50 000 FCFA
Obligatoire
Mensuelle
Fonds commun
```

Il sélectionne les membres participants.

Il ajoute une règle de sanction.

Il active la cotisation.

### Étape 4

Un membre se connecte.

Il voit la cotisation.

### Étape 5

Le membre déclare :

```text
50 000 FCFA
MTN Mobile Money
Référence de transaction
Date
Justificatif
```

### Étape 6

Le trésorier voit le paiement en attente.

Il consulte le justificatif.

Il valide le paiement.

### Étape 7

Le fonds commun est mis à jour.

L'opération est enregistrée dans l'audit.

### Étape 8

Le membre demande un prêt de :

```text
300 000 FCFA
```

Le système vérifie la politique de prêt.

### Étape 9

Le trésorier approuve le prêt.

### Étape 10

Un remboursement de :

```text
50 000 FCFA
```

est enregistré.

Le système recalcule automatiquement le solde restant.

---

# 34. TESTS

Après chaque fonctionnalité importante :

1. compiler ;
2. exécuter les tests ;
3. corriger les erreurs ;
4. vérifier les migrations ;
5. vérifier les endpoints ;
6. vérifier les permissions ;
7. vérifier les règles métier.

Le code ne doit pas être considéré comme terminé simplement parce qu'il compile.

---

# 35. MÉTHODE DE DÉVELOPPEMENT

Le développement doit être progressif.

Ordre recommandé :

```text
Phase 1
Analyse + architecture + modèle de données

Phase 2
Backend
- projet Spring Boot
- base PostgreSQL
- migrations
- entités
- repositories
- services
- DTO
- controllers
- sécurité

Phase 3
Tests backend

Phase 4
Frontend Angular

Phase 5
Intégration frontend/backend

Phase 6
Tests end-to-end

Phase 7
Préparation de la démonstration
```

Ne pas essayer de générer toute l'application en une seule opération.

---

# 36. GIT

Le projet doit être versionné avec Git.

Créer des commits réguliers après les fonctionnalités importantes.

Exemples :

```text
feat: initialize spring boot backend
feat: add authentication and roles
feat: add tontine sessions
feat: add contribution management
feat: add payment validation
feat: add sanctions
feat: add loan management
feat: initialize angular frontend
```

---

# 37. RÈGLE ESSENTIELLE POUR CLAUDE CODE

Avant d'implémenter une fonctionnalité importante :

1. analyser les fichiers existants ;
2. vérifier les règles métier dans `CLAUDE.md` ;
3. identifier les dépendances ;
4. proposer les modifications nécessaires ;
5. implémenter ;
6. compiler ;
7. tester ;
8. corriger les erreurs ;
9. résumer les fichiers modifiés.

Ne pas supprimer ou réécrire massivement du code existant sans raison.

Ne pas créer de fonctionnalité métier qui n'a pas été demandée.

Lorsqu'une information métier manque, la signaler comme :

```text
DECISION A VALIDER
```

et ne pas la transformer arbitrairement en règle.

---

# 38. DÉCISIONS MÉTIER ENCORE À VALIDER

Les éléments suivants ne doivent pas être inventés :

* montant exact de « Voir bébé » ;
* montant/règles exacts de « Banque / Épargne » ;
* règles exactes de chaque sanction ;
* délai exact avant déclenchement d'une sanction ;
* utilisation précise des fonds de « Ration » ;
* taux d'intérêt des prêts ;
* durée maximale des prêts ;
* montant minimum/maximum des prêts ;
* conditions exactes d'éligibilité aux prêts ;
* règles précises liées à l'épargne pour obtenir un prêt.

Ces éléments devront être confirmés avant leur implémentation définitive.

---

# 39. OBJECTIF FINAL DU MVP

À la fin du MVP, une personne doit pouvoir :

```text
se connecter
    ↓
consulter une séance
    ↓
voir les cotisations
    ↓
déclarer un paiement
    ↓
joindre un justificatif
    ↓
faire valider le paiement
    ↓
voir les fonds mis à jour
    ↓
demander un prêt
    ↓
faire vérifier les règles
    ↓
obtenir une décision
    ↓
effectuer un remboursement
    ↓
voir le solde restant
```

Le résultat doit être une application réellement fonctionnelle et démontrable, et non une simple maquette ou une collection d'écrans.
