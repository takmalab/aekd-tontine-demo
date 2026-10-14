# Mettre AEKD-Tontine en ligne (Railway)

L'application tourne en **un seul service** : le backend Spring Boot sert aussi
l'interface Angular, à la même adresse. Railway construit l'image à partir du
`Dockerfile` à la racine du dépôt (configuration : `railway.json`), et une base
PostgreSQL Railway stocke les données.

## Ce qu'il faut avant de commencer

* un compte sur https://railway.com (connexion avec GitHub conseillée) ;
* le code à jour sur GitHub (`takmalab/aekd-tontine-demo`, branche `master`).

## 1. Créer le projet

1. Railway → **New Project** → **Deploy from GitHub repo** → choisir
   `aekd-tontine-demo` (autoriser l'application Railway sur le dépôt si demandé).
2. Railway détecte `railway.json` et le `Dockerfile` : le premier déploiement
   démarre, puis **échoue** tant que les étapes 2 à 4 ne sont pas faites. C'est
   normal.

## 2. Ajouter la base PostgreSQL

Dans le projet : **New** → **Database** → **Add PostgreSQL**. Garder le nom
proposé, `Postgres` (il est utilisé dans les variables ci-dessous).

## 3. Variables du service de l'application

Service de l'application → onglet **Variables** → **Raw Editor**, coller puis
compléter :

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}

# Obligatoire : l'application refuse de démarrer avec le secret de développement.
APP_JWT_SECRET=<voir ci-dessous>

# Données de démonstration, avec des mots de passe propres à la version en ligne.
SPRING_PROFILES_ACTIVE=demo
APP_DEMO_ADMIN_PASSWORD=<mot de passe admin>
APP_DEMO_TRESORIER_PASSWORD=<mot de passe trésorier>
APP_DEMO_MEMBRE_PASSWORD=<mot de passe des membres fictifs>

# Facultatif : SMS réels (sinon simulation). Le fournisseur se choisit ensuite
# dans l'écran « Paramètres SMS ».
# SCMCHOST_API_KEY=sk_...
# IGOR_API_KEY=asg_...
```

`${{Postgres.…}}` sont des **références Railway** : les laisser telles quelles,
Railway les remplace par les valeurs de la base.

Pour générer `APP_JWT_SECRET` (dans PowerShell) :

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Ne jamais réutiliser ce secret ailleurs ni le committer. Le changer déconnecte
tous les utilisateurs.

## 4. Volume pour les justificatifs

Service de l'application → **Settings** → **Volumes** → **New Volume**, point de
montage : `/app/storage`. Sans volume, les justificatifs (paiements,
remboursements, épargne) seraient perdus à chaque redéploiement.

## 5. Adresse publique

Service de l'application → **Settings** → **Networking** → **Generate Domain**.
Railway fournit une adresse `https://….up.railway.app` (HTTPS inclus, nécessaire
pour les notifications push et l'installation de l'application sur téléphone).

## 6. Déployer et vérifier

* **Deployments** → **Redeploy** (ou un nouveau push sur `master`).
* Dans les journaux du déploiement, attendre `Started TontineApplication`.
  Au premier démarrage, la base est créée (migrations Flyway V1 à V21) et les
  données de démonstration sont ajoutées.
* Ouvrir l'adresse publique et se connecter avec `admin@aekd.cm` et le mot de
  passe choisi dans `APP_DEMO_ADMIN_PASSWORD`.

## Mises à jour

Chaque push sur `master` redéploie automatiquement. Les données (base et volume)
sont conservées ; les nouvelles migrations s'appliquent au démarrage.

## En cas de problème

| Symptôme dans les journaux | Cause |
|---|---|
| `Secret JWT de développement interdit` | `APP_JWT_SECRET` absent |
| `Connection refused` / `password authentication failed` | variables `SPRING_DATASOURCE_*` ou base non créée |
| Le healthcheck `/login` échoue | l'application n'a pas démarré : lire l'erreur au-dessus dans les journaux |

## Coûts

Railway est payant au-delà de la période d'essai (de l'ordre de 5 $ par mois
pour ce projet : service, base et volume). Les SMS réels sont facturés à part
par le fournisseur.
