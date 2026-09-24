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

## Décision actuelle

Les exports :

* Excel ;
* PDF ;

## Statut

```text
VALIDER
```

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
