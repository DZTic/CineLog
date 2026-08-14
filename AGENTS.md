# CinéLog - Directives Générales pour les Agents (AGENTS.md)

Ce document définit les règles, conventions et directives architecturales que tout agent et développeur travaillant sur le projet **CinéLog** doit respecter.

---

## 🎯 Vue d'ensemble du Projet

**CinéLog** est une application Android moderne (type Letterboxd) dédiée au suivi de films, séries TV et animes.
- **Stack Technique** : Kotlin 2.0+, Jetpack Compose avec Material 3 (Thème Dark Cinema), Navigation Compose, Room Database avec KSP, Retrofit 2 / Moshi, Coil Compose, Coroutines & StateFlow.
- **Backend / Proxy** : Cloudflare Worker serverless (`proxy/`) relayant les requêtes TMDB avec mise en cache edge et injection sécurisée des clés.
- **APIs Intégrées** : TMDB (Films & Séries TV) et Jikan (MyAnimeList - Animes).

---

## 🏛️ Principes d'Architecture & Conventions

### 1. Architecture MVVM & Unidirectional Data Flow (UDF)
- **ViewModel** : Expose l'état UI sous forme de `StateFlow<UiState>` immuable. Les événements utilisateurs sont passés via des méthodes explicites (ex: `onAction(event)` ou méthodes d'intention).
- **Repository** : Gère la synchronisation entre la base locale Room (Offline-first) et les services distants Retrofit.
- **Gestion des états d'affichage** : Prévoir systématiquement les 3 états principaux : Chargement (Skeleton/Shimmer), Succès (Contenu), Erreur / Vide (Empty state avec action de retry).

### 2. Interface Utilisateur & Jetpack Compose
- Respecter le thème **Dark Cinema** basé sur Material 3 (`com.example.ui.theme`).
- Éviter les recompositions inutiles : utiliser `remember`, `derivedStateOf`, et des clés stables (`key`) dans les `LazyColumn` / `LazyRow`.
- Chargement d'images : Utiliser Coil avec `AsyncImage` avec placeholder et gestion d'erreur.
- Accessibilité & i18n : Fournir des `contentDescription` explicites pour les éléments visuels significatifs et utiliser les ressources de chaînes (`strings.xml`).

### 3. Base de Données Room & Migrations
- Tout changement de schéma dans les entités Room requiert :
  1. L'incrémentation de la version dans `AppDatabase.kt` (`version = CURRENT_VERSION + 1`).
  2. L'export du schéma JSON dans `app/schemas/com.example.data.AppDatabase/`.
  3. L'écriture d'un objet `Migration(X, X+1)` explicite et son enregistrement dans le builder Room.
  4. L'ajout d'un test unitaire de migration dans `app/src/test/java/com/example/data/MigrationTest.kt`.
- Indexation : Ajouter des index (`@Index`) sur les clés étrangères et les colonnes fréquemment requêtées (ex: `titleId`, `mediaType`, `status`).

### 4. Réseau, Rate-Limiting & Gestion des Secrets
- **Proxy TMDB** : Privilégier les appels via le proxy Cloudflare (`BuildConfig.TMDB_PROXY_BASE_URL`) lorsque l'utilisateur n'a pas configuré de clé API personnelle.
- **Rate-Limiting Jikan / MyAnimeList** : Respecter le délai minimum entre requêtes consécutives via `JikanRateLimitInterceptor.kt` avec gestion automatique du code HTTP 429 et backoff exponentiel.
- **Secrets & Configuration** : Ne **JAMAIS** committer de clés API en clair dans les fichiers sources. Le projet utilise le plugin `Secrets Gradle Plugin` branché sur `.env` avec fallback automatique sur `.env.example`.

---

## 🛠️ Commandes de Validation Usuelles

- **Vérification des Conflits de PR** :
  - Bash : `./scripts/check-pr-conflicts.sh origin/main`
  - PowerShell : `.\scripts\check-pr-conflicts.ps1 -TargetBranch "origin/main"`
- **Consolidation Automatique de Toutes les PRs** :
  - CI : Workflow GitHub Actions `Consolidate Open PRs`
  - Bash : `./scripts/consolidate-prs.sh --dry-run` ou `./scripts/consolidate-prs.sh --push --create-pr`
  - PowerShell : `.\scripts\consolidate-prs.ps1 -DryRun` ou `.\scripts\consolidate-prs.ps1 -Push -CreatePr`
- **Compiler l'APK Debug** : `./gradlew assembleDebug`
- **Exécuter les Tests Unitaires & Migrations** : `./gradlew testDebugUnitTest`
- **Vérifier le Linter Android** : `./gradlew lintDebug`
- **Tester le Proxy Cloudflare** : `cd proxy && npx wrangler dev`

---

## 🤖 Agents Spécialisés Disponibles

Pour les tâches spécifiques, des agents spécialisés sont disponibles dans le dossier [`.agents/agents/`](file:///c:/Users/gener/Downloads/CineLog/.agents/agents/) :

- **`pr-conflict-guardian`** ([`.agents/agents/pr-conflict-guardian/agent.md`](file:///c:/Users/gener/Downloads/CineLog/.agents/agents/pr-conflict-guardian/agent.md)) :
  - Détection préventive des divergences avec `origin/main`, réconciliation propre du code et résolution des conflits sans régression.
- **`android-expert`** ([`.agents/agents/android-expert/agent.md`](file:///c:/Users/gener/Downloads/CineLog/.agents/agents/android-expert/agent.md)) :
  - Spécialiste interface Jetpack Compose, Material 3, ViewModel, Flow, Navigation et architecture UDF.
- **`qa-reviewer`** ([`.agents/agents/qa-reviewer/agent.md`](file:///c:/Users/gener/Downloads/CineLog/.agents/agents/qa-reviewer/agent.md)) :
  - Spécialiste tests unitaires JVM/Robolectric, linter Android, intégrité des schémas Room, ProGuard/R8 et détection de régressions.
- **`proxy-dev`** ([`.agents/agents/proxy-dev/agent.md`](file:///c:/Users/gener/Downloads/CineLog/.agents/agents/proxy-dev/agent.md)) :
  - Spécialiste Cloudflare Worker serverless, caching edge et routage des requêtes API TMDB.
- **`compose-ui`** ([`.agents/agents/compose-ui.md`](file:///c:/Users/gener/Downloads/CineLog/.agents/agents/compose-ui.md)) :
  - Spécialiste composants, thèmes, micro-animations et design system Material 3.
- **`data-architecture`** ([`.agents/agents/data-architecture.md`](file:///c:/Users/gener/Downloads/CineLog/.agents/agents/data-architecture.md)) :
  - Spécialiste Room, DAO, entités, Retrofit, Moshi et proxy TMDB.
- **`qa-build`** ([`.agents/agents/qa-build.md`](file:///c:/Users/gener/Downloads/CineLog/.agents/agents/qa-build.md)) :
  - Spécialiste Gradle Kotlin DSL, KSP, ProGuard/R8, tests Robolectric/Roborazzi et CI GitHub Actions.
