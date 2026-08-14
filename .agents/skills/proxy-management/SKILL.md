---
name: proxy-management
description: >-
  Procédures pour configurer, exécuter en local, tester et déployer le proxy TMDB Cloudflare Worker de CinéLog.
---

# Skill : Gestion et Déploiement du Proxy Cloudflare Worker

Ce guide détaille les commandes et procédures pour administrer le proxy TMDB situé dans le dossier `proxy/`.

## 📁 Structure du Dossier Proxy

- `proxy/worker.js` : Logique JavaScript du relai et de la mise en cache.
- `proxy/wrangler.toml` : Configuration Cloudflare Worker.
- `proxy/README.md` : Documentation spécifique du proxy.

---

## 🚀 Procédures Opérationnelles

### 1. Installation des dépendances
Se placer dans le dossier `proxy` :
```bash
cd proxy
npm install
```

### 2. Démarrer l'environnement de développement local
Pour émuler le worker localement avec Wrangler :
```bash
cd proxy
npx wrangler dev
```
Le worker écoutera par défaut sur `http://localhost:8787`.

### 3. Tester les Endpoints en Local
- Requête de statut / racine :
  ```bash
  curl http://localhost:8787/
  ```
- Recherche d'un film :
  ```bash
  curl "http://localhost:8787/search/movie?query=Inception"
  ```

### 4. Configurer les Secrets Cloudflare
Pour enregistrer le token TMDB dans les secrets du worker distant :
```bash
cd proxy
npx wrangler secret put TMDB_BEARER_TOKEN
```
Entrer ensuite le token TMDB lorsqu'invité.

### 5. Déployer en Production
Pour publier le worker sur Cloudflare :
```bash
cd proxy
npx wrangler deploy
```
Vérifier l'URL générée et s'assurer qu'elle correspond à la variable `TMDB_PROXY_BASE_URL` dans `.env` côté application Android.
