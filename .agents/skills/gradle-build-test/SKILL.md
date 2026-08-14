---
name: gradle-build-test
description: >-
  Procédures pour compiler le projet Android CinéLog, exécuter la suite de tests unitaires (JUnit/Robolectric) et lancer l'analyse de qualité Lint.
---

# Skill : Compilation, Tests et Linting Gradle pour CinéLog

Ce guide pas-à-pas explique comment compiler, tester et vérifier la qualité du code du projet Android **CinéLog**.

## 🛠️ Commandes Principales

### 1. Compilation de l'APK Debug
Pour vérifier qu'il n'y a aucune erreur de syntaxe ou de compilation Kotlin :
```bash
./gradlew assembleDebug --stacktrace
```

### 2. Exécution des Tests Unitaires
Pour lancer tous les tests unitaires JVM et Robolectric :
```bash
./gradlew testDebugUnitTest
```
- Pour exécuter une classe de test spécifique :
  ```bash
  ./gradlew testDebugUnitTest --tests "com.example.data.CineLogRepositoryTest"
  ```
- Rapport de tests généré à : `app/build/reports/tests/testDebugUnitTest/index.html`

### 3. Analyse Statique et Linter Android
Pour détecter d'éventuels bugs, erreurs de ressources ou problèmes d'accessibilité :
```bash
./gradlew lintDebug
```
- Rapport de lint généré à : `app/build/reports/lint-results-debug.html`

---

## 🔍 Diagnostic des Problèmes Fréquents

1. **Erreur de mémoire ou daemon Gradle bloqué** :
   ```bash
   ./gradlew --stop
   ./gradlew clean assembleDebug
   ```

2. **Problème de configuration `.env` manquant** :
   Vérifier que `.env` existe à la racine du projet. Si manquant, le créer à partir de `.env.example` :
   ```bash
   cp .env.example .env
   ```

3. **Incohérence des caches Gradle / Kotlin** :
   Supprimer les dossiers `.gradle` et `build` si des erreurs de symboles persistantes surviennent.
