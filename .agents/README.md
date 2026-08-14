# Antigravity Custom Agents - CineLog 🎬

Ce dossier contient la configuration des **Agents Personnalisés Antigravity** pour le projet Android **CineLog**, conformément au système d'agents introduit dans [Antigravity 2.0](https://antigravity.google/blog/introducing-custom-agents).

---

## 🤖 Agents Disponibles

| Agent | Fichier | Description & Rôle |
| :--- | :--- | :--- |
| **`compose-ui`** | [`.agents/agents/compose-ui.md`](./agents/compose-ui.md) | Spécialiste interface Jetpack Compose, Material 3, navigation, animations, Coil et architecture UDF pour les écrans et composants. |
| **`data-architecture`** | [`.agents/agents/data-architecture.md`](./agents/data-architecture.md) | Spécialiste persistance Room (entités, DAO, migrations), couche réseau Retrofit/OkHttp, gestion du rate-limiting Jikan et proxy Cloudflare Worker. |
| **`qa-build`** | [`.agents/agents/qa-build.md`](./agents/qa-build.md) | Spécialiste configuration Gradle Kotlin DSL, KSP, ProGuard/R8, tests unitaires Robolectric/Roborazzi, linting et workflows CI GitHub Actions. |

---

## 🚀 Utilisation des Agents

### 1. En Session Principale (CLI ou GUI)

- **Via la CLI Antigravity (`agy`)** :
  ```bash
  # Lancer une session avec le spécialiste UI & Jetpack Compose
  agy --agent compose-ui

  # Lancer une session avec le spécialiste Base de données & Réseau
  agy --agent data-architecture

  # Lancer une session avec le spécialiste Build, Qualité & CI/CD
  agy --agent qa-build
  ```

- **Via l'interface graphique (Antigravity GUI)** :
  Sélectionnez l'agent directement depuis le menu déroulant des agents dans la barre latérale ou tapez `/` dans le chat pour invoquer les commandes d'agents.

### 2. En Subagent Autonome (Délégation en arrière-plan)

L'agent principal ou l'orchestrateur peut déléguer des sous-tâches spécifiques à ces agents en arrière-plan sans saturer la fenêtre de contexte principale :
- Un agent principal qui conçoit une nouvelle fonctionnalité de suivi peut invoquer `data-architecture` pour mettre à jour la base Room et `compose-ui` pour les écrans en parallèle.
- `qa-build` peut être invoqué à la fin d'une tâche pour exécuter les tests (`./gradlew testDebugUnitTest`) et vérifier que la compilation R8/ProGuard et le lint passent sans erreur.
