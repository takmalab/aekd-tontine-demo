# AEKD – Gestion de Tontine : Rapport d'analyse

| Élément | Valeur |
|---|---|
| Date du rapport | 2 octobre 2026 |
| Objet | Analyse fonctionnelle et technique de l'application AEKD-Tontine (MVP web) |
| Références | CLAUDE.md, docs/regles-metier.md, docs/decisions.md, code source (branche master) |
| Statut du MVP | Backend et frontend fonctionnels ; points métier encore à valider |

## 1. Contexte et objectif

AEKD-Tontine est l'application web de gestion de la tontine de l'Amicale des Enfants Kamkop de Douala (AEKD). Elle remplace un suivi manuel des cotisations, des paiements, des sanctions, de l'épargne et des prêts par un outil centralisé, traçable et accessible selon le rôle de chaque utilisateur.

L'objectif actuel est un **MVP web fonctionnel et démontrable**, couvrant le flux complet : connexion, consultation d'une séance, déclaration et validation d'un paiement, mise à jour des fonds, demande et décision de prêt, remboursement et calcul du solde restant.

Ce rapport présente :

- l'analyse des besoins métier ;
- les acteurs et leurs droits ;
- le modèle de données retenu ;
- l'architecture technique ;
- l'état de couverture du périmètre MVP ;
- les écarts constatés et les décisions métier encore à valider.

## 2. Analyse des besoins métier

### 2.1 Concepts centraux

| Concept | Rôle dans le système |
|---|---|
| Séance | Jour de réunion unique (date, lieu, membre récepteur, bénéficiaires de la séance). Concept central auquel se rattachent les cotisations. |
| Cotisation (ContributionDefinition) | Définition métier : nom, montant, mode de montant, fréquence, caractère obligatoire, visibilité, destination des fonds, statut. |
| Période (ContributionPeriod) | Application d'une cotisation à une séance donnée, avec échéance. Permet le suivi par membre, par cotisation et par séance. |
| Paiement (ContributionTransaction) | Déclaration manuelle d'un membre, validée ou rejetée par le trésorier. Seul un paiement validé compte dans les fonds. |
| Justificatif (PaymentProof) | Fichier JPG, JPEG, PNG ou PDF joint à un paiement. |
| Sanction | Règle configurée (SanctionRule) distincte de la sanction réellement appliquée (AppliedSanction). |
| Fonds | Deux destinations strictement séparées : fonds commun (TONTINE_FUND) et épargne individuelle (INDIVIDUAL_SAVINGS). |
| Prêt | Demande évaluée selon une politique configurable (LoanPolicy), puis approuvée ou rejetée, puis remboursée. |
| Audit | Journal des opérations sensibles. |

### 2.2 Cotisations connues de l'AEKD

| Cotisation | Montant | Obligatoire | Destination |
|---|---|---|---|
| Cotisation 50 000 | 50 000 FCFA | Oui | Fonds commun |
| Cotisation 10 000 | 10 000 FCFA | Oui | Fonds commun |
| Ration | 2 000 FCFA | Oui | Fonds commun (usage précis à valider) |
| Voir bébé | À définir | Non | À confirmer |
| Banque / Épargne | Configurable | Non | Épargne individuelle |

Les trois cotisations obligatoires restent **trois cotisations distinctes** ; elles ne sont jamais fusionnées. Le montant obligatoire connu par séance est de 50 000 + 10 000 + 2 000 = **62 000 FCFA**, hors cotisations facultatives.

### 2.3 Règles métier structurantes

- Un membre ne peut déclarer un paiement que pour une cotisation dont il est participant.
- Une cotisation PUBLIC est visible de tous ; une cotisation PRIVATE n'est visible que par l'ADMIN, le TRESORIER et les participants.
- Les bénéficiaires sont indépendants des participants.
- Un paiement déclaré n'est pas encaissé tant qu'il n'est pas validé ; un membre ne peut pas valider son propre paiement.
- Le fonds commun et l'épargne individuelle ne sont jamais mélangés.
- Une cotisation facultative non payée ne génère pas de sanction pour non-paiement.
- Les règles de prêt sont configurables (LoanPolicy), jamais codées en dur.
- Un prêt peut être remboursé en plusieurs fois ; un remboursement supérieur au solde restant est refusé ; un solde nul fait passer le prêt à REPAID.
- Les montants financiers sont stockés en NUMERIC (BigDecimal côté Java), jamais en type flottant.

### 2.4 Cycle de vie et statuts

| Objet | Statuts |
|---|---|
| Utilisateur | PENDING_VALIDATION, ACTIVE, DISABLED |
| Cotisation | DRAFT, ACTIVE, INACTIVE |
| Situation d'un membre sur une période | PAID, PENDING, PARTIAL, LATE, NOT_PAID |
| Paiement | PENDING, VALIDATED, REJECTED |
| Sanction appliquée | APPLIED, CANCELLED |
| Prêt | REQUESTED, APPROVED, IN_PROGRESS, REJECTED, REPAID |

Le statut PARTIAL a été ajouté aux quatre statuts prévus par CLAUDE.md §13 pour distinguer un membre ayant payé une partie du montant. Un membre partiellement payé reste candidat à la sanction de retard (décision du porteur du projet du 25/09/2026).

## 3. Acteurs et droits

| Fonction | ADMIN | TRESORIER | MEMBRE |
|---|---|---|---|
| Gérer les utilisateurs et valider les inscriptions | Oui | – | – |
| Gérer les membres | Oui (modification) | Consultation | Son profil |
| Créer les séances | Oui | Oui | Consultation |
| Créer, configurer et activer les cotisations | Oui | Oui | Consultation selon visibilité |
| Gérer participants et bénéficiaires | Oui | Oui | – |
| Déclarer un paiement et joindre un justificatif | – | – | Oui (si participant) |
| Valider / rejeter un paiement | – | Oui | – |
| Configurer les règles de sanction | Oui | Oui | – |
| Appliquer / annuler une sanction | – | Oui | Consulte les siennes |
| Configurer les politiques de prêt | Oui | – | – |
| Demander un prêt | – | – | Oui |
| Approuver / rejeter un prêt | – | Oui | – |
| Enregistrer un remboursement | – | Oui | Consulte les siens |
| Consulter fonds commun et épargnes | Oui | Oui | Sa propre épargne |
| Consulter le journal d'audit | Oui | – | – |

Les droits sont appliqués côté serveur par `@PreAuthorize` et par des contrôles d'appartenance dans les services, et côté client par des gardes de route (auth.guard, role.guard).

## 4. Modèle de données

### 4.1 Entités

Le modèle implémente l'ensemble des concepts exigés par CLAUDE.md §27 : User, Role, Member, Session, ContributionDefinition, ContributionParticipant, ContributionBeneficiary, ContributionPeriod, ContributionTransaction, PaymentProof, SanctionRule, AppliedSanction, LoanPolicy, Loan, LoanRuleEvaluation, LoanRepayment, AuditLog. Une entité SessionBeneficiary a été ajoutée pour les bénéficiaires d'une séance, distincts des bénéficiaires d'une cotisation.

### 4.2 Relations principales

```text
User 1 ─── 1 Member
Session 1 ─── N ContributionDefinition
Session 1 ─── N SessionBeneficiary ─── Member
ContributionDefinition 1 ─── N ContributionParticipant
ContributionDefinition 1 ─── N ContributionBeneficiary
ContributionDefinition 1 ─── N ContributionPeriod
ContributionDefinition 1 ─── N SanctionRule
SanctionRule 1 ─── N AppliedSanction
ContributionPeriod 1 ─── N ContributionTransaction
ContributionTransaction 1 ─── 0..1 PaymentProof
LoanPolicy 1 ─── N Loan
Loan 1 ─── N LoanRuleEvaluation
Loan 1 ─── N LoanRepayment
```

### 4.3 Migrations Flyway

| Version | Contenu |
|---|---|
| V1 – V3 | Rôles, utilisateurs, membres (et rôles initiaux) |
| V4 | Séances |
| V5 – V7 | Cotisations, participants, bénéficiaires, périodes, transactions, justificatifs |
| V8 | Règles de sanction et sanctions appliquées |
| V9 | Politiques de prêt, prêts, évaluations de règles, remboursements |
| V10 | Journal d'audit |
| V11 | Remplacement de `enabled` par le statut utilisateur |
| V12 | Séance à date unique, lieu, membre récepteur, bénéficiaires de séance |

### 4.4 Évolution notable du modèle

La migration V12 transforme la séance d'une période (date de début / date de fin) en **un jour de réunion unique** avec un lieu, un membre récepteur et des bénéficiaires. Cette évolution, décidée par le porteur du projet, est documentée dans la migration elle-même.

## 5. Architecture technique

### 5.1 Pile technique

| Couche | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Web, Spring Data JPA, Spring Security + JWT, Bean Validation, Maven, OpenAPI / Swagger |
| Base de données | PostgreSQL, migrations Flyway, base de test isolée (aekd_tontine_test) |
| Frontend | Angular 22, TypeScript, Angular Material, RxJS, Router, Reactive Forms, intercepteurs HTTP, gardes de route |
| Environnement | Docker Compose (développement), déploiement backend préparé pour Railway |

### 5.2 Organisation du backend

Architecture en couches Controller → Service → Repository → Base de données, avec des DTO en entrée et en sortie (aucune entité JPA exposée). Modules : auth, user, member, session, contribution, sanction, loan, repayment, fund, dashboard, audit, storage, security, config, common.

### 5.3 Abstractions prévues pour l'évolution

```text
PaymentService
    └── ManualPaymentService        (MVP : déclaration manuelle)
        → plus tard : MtnMomoPaymentService, OrangeMoneyPaymentService

FileStorageService
    └── LocalFileStorageService     (MVP : stockage local)
        → plus tard : Amazon S3, Cloudinary, Google Cloud Storage
```

### 5.4 Organisation du frontend

`core/` (service d'authentification, gardes, intercepteurs d'authentification et d'erreurs), `shared/` (composants réutilisables : badge de statut, boîte d'information…), `layout/` (sidebar, topbar) et `features/` : auth, dashboard, sessions, contributions (liste, détail, assistant de création), payments, sanctions, loans (politiques, demandes, décisions, remboursements), members, users, audit.

### 5.5 Principales API

| Domaine | Endpoints |
|---|---|
| Authentification | POST /api/auth/login, POST /api/auth/register |
| Utilisateurs | /api/users (CRUD, /pending, /{id}/approve) |
| Membres | GET /api/members, /mine, /{id} ; PUT /api/members/{id} |
| Séances | GET/POST /api/sessions, GET /api/sessions/{id} |
| Cotisations | GET/POST /api/contributions, /{id}/activate, /{id}/participants, /{id}/beneficiaries, /{id}/periods, statuts des participants par période |
| Paiements | POST /api/contributions/{id}/payments, /payments/pending, /payments/mine, /payments/{id}/validate, /reject, /proof |
| Fonds | GET /api/funds/tontine, /api/savings, /api/savings/mine, /api/savings/{memberId} |
| Sanctions | /api/sanctions (règles), /{ruleId}/candidates, /{ruleId}/apply, /applied, /applied/mine, /applied/{id}/cancel |
| Prêts | /api/loan-policies (CRUD, activation), /api/loans, /mine, /{id}/approve, /{id}/reject |
| Remboursements | /api/loans/{loanId}/repayments, /api/loans/{loanId}/balance |
| Pilotage | GET /api/dashboard, GET /api/dashboard/members, GET /api/audit |

## 6. Couverture du périmètre MVP

| Fonctionnalité (CLAUDE.md §3) | Backend | Frontend |
|---|---|---|
| Authentification, inscription, validation des comptes | Fait | Fait |
| Gestion des utilisateurs, membres et rôles | Fait | Fait |
| Gestion des séances | Fait | Fait |
| Création / configuration / activation des cotisations | Fait | Fait (assistant) |
| Participants et bénéficiaires | Fait | Fait |
| Déclaration de paiement et justificatif | Fait | Fait |
| Validation / rejet des paiements | Fait | Fait |
| Sanctions (règles, candidats, application, annulation) | Fait | Fait |
| Fonds commun et épargne individuelle | Fait | Via les tableaux de bord |
| Politiques de prêt, demande, décision | Fait | Fait |
| Remboursements et solde restant | Fait | Fait |
| Tableaux de bord ADMIN/TRESORIER et MEMBRE | Fait | Fait |
| Journal d'audit | Fait | Fait |

## 7. Écarts constatés par rapport au cahier des charges

Les points suivants diffèrent de CLAUDE.md et doivent être soit corrigés, soit validés comme choix assumés :

1. **Modification / suppression de cotisation** : CLAUDE.md §28 prévoit `PUT` et `DELETE /api/contributions/{id}` ; seule l'activation existe actuellement.
2. **Configuration des politiques de prêt** : CLAUDE.md §20 la confie au « trésorier ou à l'administrateur » ; l'API la réserve aujourd'hui à l'ADMIN.
3. **Remboursements par le membre** : CLAUDE.md §6 indique qu'un membre peut « enregistrer/consulter ses remboursements » ; l'enregistrement est aujourd'hui réservé au TRESORIER.
4. **Écran dédié aux fonds** : le module `cash` prévu au §29 n'existe pas ; le fonds commun et l'épargne ne sont visibles que via les tableaux de bord.
5. **Statut PARTIAL** : ajouté au-delà des quatre statuts du §13 ; ajout cohérent mais à documenter dans regles-metier.md.
6. **Séance à date unique** : le CLAUDE.md décrit encore la séance comme une « période » ; la documentation de référence doit être alignée sur la migration V12.

## 8. Décisions métier encore à valider

Conformément au principe de non-invention, aucune de ces valeurs n'est codée en dur : elles restent configurables ou en attente.

| Sujet | Statut | Traitement actuel dans l'application |
|---|---|---|
| Montant de « Voir bébé » et destination | À VALIDER | Montant configurable |
| Montant / règles de « Banque / Épargne » | À VALIDER | Montant configurable, destination épargne individuelle |
| Utilisation précise des fonds de « Ration » | À VALIDER | Rattachée au fonds commun |
| Délais de paiement par cotisation | À VALIDER | Échéance saisie par période |
| Seuil de retard | À VALIDER | Seuil configurable dans la règle de sanction |
| Montants des sanctions financières et sanctions en nature | À VALIDER | Saisis librement dans la règle |
| Application automatique ou proposée des sanctions | À VALIDER | Détection automatique des candidats, application manuelle par le trésorier uniquement |
| Montants min/max, durée maximale des prêts | À VALIDER | Paramètres de la LoanPolicy |
| Taux, mode de calcul et fréquence des intérêts | À VALIDER (intérêt : OUI) | Taux stocké ; solde calculé sur le capital approuvé uniquement |
| Remboursement : capital seul, capital + intérêt, frais | À VALIDER | Capital seul en attendant |
| Épargne minimale, ancienneté, nombre de prêts actifs | À VALIDER | Paramètres optionnels de la LoanPolicy |
| Mécanique des fonds disponibles pour les prêts | DÉCIDÉ sur le principe, mécanique à préciser | Non contrôlée lors de l'approbation |

## 9. Risques et points d'attention

- **Calcul des intérêts** : tant que le mode de calcul n'est pas validé, le solde d'un prêt ne comprend que le capital. C'est le principal point bloquant pour une utilisation réelle.
- **Fonds disponibles pour les prêts** : sans mécanique définie, rien n'empêche d'approuver un prêt supérieur aux fonds réellement disponibles.
- **Stockage local des justificatifs** : adapté au MVP, mais non persistant sur certaines plateformes d'hébergement (conteneurs éphémères) ; prévoir un stockage cloud avant la production.
- **Identifiants de démonstration** : à désactiver ou changer avant toute mise en production.
- **Plantages de la JVM** : des fichiers `hs_err_pid*.log` présents dans le dépôt indiquent des arrêts brutaux de la JVM en environnement local (probablement liés à la mémoire disponible) ; à analyser et à exclure du versionnement.

## 10. Recommandations et prochaines étapes

1. Faire valider par le bureau de l'AEKD les décisions listées en section 8, en priorité les intérêts et les règles de sanction.
2. Trancher les écarts de la section 7 (droits du trésorier sur les politiques de prêt, remboursements saisis par le membre, modification/suppression des cotisations).
3. Mettre à jour CLAUDE.md et regles-metier.md (séance à date unique, statut PARTIAL).
4. Ajouter un écran dédié aux fonds (fonds commun et épargne par membre).
5. Dérouler et enregistrer le scénario de démonstration complet (CLAUDE.md §33) de bout en bout.
6. Préparer la production : stockage cloud des justificatifs, secrets JWT, suppression des comptes de démonstration, sauvegardes PostgreSQL.
