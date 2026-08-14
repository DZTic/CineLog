# CinéLog - Directives Générales pour les Agents (AGENTS.md)

Ce document définit les règles, conventions et directives architecturales que tout agent travaillant sur le projet **CinéLog** doit respecter.

---

## 🎯 Vue d'ensemble du Projet

**CinéLog** est une application Android moderne (type Letterboxd) dédiée au suivi de films, séries TV et animes.
- **Stack** : Kotlin 2.0+, Jetpack Compose, Material 3 (Thème Dark Cinema), Room Database, Retrofit/Moshi, Coil, Coroutines & Flow.
- **Backend / Proxy** : Cloudflare Worker serverless (`proxy/`) relayant les requêtes TMDB avec mise en cache edge.
- **APIs** : TMDB (Films / Séries) et Jikan (MyAnimeList - Animes).

---

## 🏛️ Principes d'Architecture & Conventions

### 1. Architecture MVVM & Unidirectional Data Flow (UDF)
- **ViewModel** : Expose l'état UI sous forme de `StateFlow<UiState>` immuable. Les événements utilisateurs sont passés via des méthodes explicites (ex: `onAction(event)` ou méthodes d'intention).
- **Repository** : Gère la synchronisation entre la base locale Room (Offline-first) et les services distants Retrofit.
- **Data Layer** : Séparation stricte entre les entités Room (`@Entity`), les DTOs réseau (`@JsonClass(generateAdapter = true)`) et les modèles de domaine si applicable.

### 2. Interface Utilisateur & Jetpack Compose
- Respecter le thème **Dark Cinema** basé sur Material 3 (`com.example.ui.theme`).
- Éviter les recompositions inutiles : utiliser `remember`, `derivedStateOf`, et des classes stables (`@Immutable` / `@Stable` si nécessaire).
- Chargement d'images : Utiliser Coil avec `AsyncImage` ou `rememberAsyncImagePainter` avec placeholder et gestion d'erreur.
- Responsive & Accessibility : Veiller aux tailles de cibles tactiles (min 48dp) et aux descriptions de contenu (`contentDescription`).

### 3. Base de Données Room & Migrations
- Tout changement de schéma dans les entités Room requiert :
  1. L'incrémentation de la version dans `AppDatabase` (`version = X`).
  2. L'export du schéma JSON dans `app/schemas/`.
  3. L'écriture d'un objet `Migration(X, X+1)` explicite et son enregistrement dans le builder Room.
  4. L'ajout d'un test unitaire de migration dans `app/src/test/`.

### 4. Gestion des Secrets & Configuration
- Ne **JAMAIS** committer de clés API en clair dans les fichiers sources.
- Utiliser `.env` (chargé via `secrets-gradle-plugin`) pour configurer les URLs et clés d'API.

---

## 🛠️ Commandes de Validation Usuelles

- **Compiler l'APK Debug** : `./gradlew assembleDebug`
- **Exécuter les Tests Unitaires** : `./gradlew testDebugUnitTest`
- **Vérifier le Linter Android** : `./gradlew lintDebug`
- **Tester le Proxy Cloudflare** : `cd proxy && npx wrangler dev`

---

## 🤖 Agents Spécialisés Disponibles

- **`android-expert`** (`.agents/agents/android-expert/agent.md`) : Architecture Android, Jetpack Compose, ViewModel, Flow.
- **`qa-reviewer`** (`.agents/agents/qa-reviewer/agent.md`) : Tests unitaires, Linting, validation Room et régressions.
- **`proxy-dev`** (`.agents/agents/proxy-dev/agent.md`) : Maintenance et déploiement du Cloudflare Worker TMDB.
