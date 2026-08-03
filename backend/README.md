# CineLog TMDB proxy

Proxy HTTP léger (zéro dépendance, Node >= 18) qui relaie les appels vers
l'API TMDB en injectant la clé côté serveur. L'application Android fonctionne
ainsi dès l'installation, sans que chaque utilisateur configure sa propre clé.

## Lancer

```sh
TMDB_API_KEY=<votre_clé_tmdb_v3> npm start   # écoute sur PORT (défaut 3000)
```

## Déployer

N'importe quel hébergeur Node convient (Render, Railway, Fly.io, VPS...).
Configurez la variable d'environnement `TMDB_API_KEY`, puis renseignez l'URL
du service dans `tmdbProxyApi` de `app/src/main/java/com/example/data/Repository.kt`.

Le proxy écrase tout paramètre `api_key` fourni par le client : la clé reste
uniquement côté serveur. Les requêtes sont relayées telles quelles vers
`https://api.themoviedb.org/3<path>` (ex. `/search/movie?query=...`).
