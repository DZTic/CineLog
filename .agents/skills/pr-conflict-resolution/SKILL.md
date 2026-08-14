---
name: pr-conflict-resolution
description: >-
  Procédures pour détecter, prévenir et résoudre les conflits de fusion Git sur les Pull Requests de CinéLog, avec simulation via git merge-tree et rebase propre.
---

# Skill : Prévention et Résolution des Conflits de PR pour CinéLog

Ce guide détaille la méthodologie pour inspecter une branche de fonctionnalité, simuler la fusion avec `origin/main` et résoudre proprement les conflits sans introduire de régression.

---

## 🔍 1. Détection Préventive de Conflit

Avant d'ouvrir une PR ou d'effectuer un `push` :

### Méthode 1 : Script Automatisé
```bash
# Bash
./scripts/check-pr-conflicts.sh origin/main

# PowerShell
.\scripts\check-pr-conflicts.ps1 -TargetBranch "origin/main"
```

### Méthode 2 : Analyse `git merge-tree`
```bash
# Récupérer les derniers commits de la branche principale
git fetch origin main

# Identifier la base commune
MERGE_BASE=$(git merge-base HEAD origin/main)

# Simuler la fusion en 3 points sans toucher aux fichiers locaux
git merge-tree $MERGE_BASE HEAD origin/main
```

---

## 🛠️ 2. Procédure de Résolution Standard (Rebase)

Quand un conflit est détecté, la procédure privilégiée est le rebase propre :

1. **Synchroniser la branche principale** :
   ```bash
   git fetch origin main
   ```
2. **Démarrer le rebase** :
   ```bash
   git rebase origin/main
   ```
3. **Inspecter l'état des conflits** :
   ```bash
   git status
   ```
4. **Résoudre les fichiers conflictuels** :
   - Ouvrir chaque fichier affichant `both modified`.
   - Supprimer les marqueurs `<<<<<<<`, `=======`, `>>>>>>>` en conservant l'ensemble des éléments pertinents.
5. **Marquer les fichiers comme résolus et continuer** :
   ```bash
   git add <fichier1> <fichier2>
   git rebase --continue
   ```
6. **Mettre à jour la PR distante** :
   ```bash
   git push --force-with-lease origin <nom-de-branche>
   ```

---

## 📋 3. Règles de Réconciliation par Type de Fichier

| Type de Fichier | Exemple | Règle de Résolution |
| :--- | :--- | :--- |
| **Directives & Agents** | `AGENTS.md`, `.agents/` | **Cumulatif** : Intégrer les nouvelles directives et lister l'ensemble des agents sans écraser les agents de l'autre branche. |
| **Catalogue de Versions** | `gradle/libs.versions.toml` | **Union** : Conserver toutes les entrées `[versions]`, `[libraries]` et `[plugins]` ajoutées des deux côtés. Choisir la version la plus récente compatible. |
| **Base de Données Room** | `AppDatabase.kt`, `Migrations.kt` | **Séquentiel** : Ne jamais régresser la version de base de données. Incrémenter si deux migrations concurrentes ont pris le même numéro de version. |
| **Scripts Gradle** | `app/build.gradle.kts` | **Fusion des blocs** : Conserver les plugins, les règles ProGuard/R8 et les dépendances des deux branches. |
| **Ressources Android** | `strings.xml`, `colors.xml` | **Union** : Conserver toutes les clés de chaînes et de couleurs nouvelles sans doublon d'identifiant. |

---

## 🧪 4. Validation Obligatoire

Après tout rebase ou résolution :
```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```
