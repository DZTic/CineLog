# CineLog TMDB proxy

Un Cloudflare Worker minimal qui porte la vraie clé TMDB côté serveur, pour
que l'app fonctionne sans que chaque utilisateur ait besoin de créer un
compte développeur TMDB. Une clé personnelle reste possible dans
Paramètres pour ceux qui préfèrent leur propre quota — dans ce cas l'app
tape directement sur TMDB et ignore ce proxy.

Remplace l'ancien backend Node/Render (`backend/`, plan gratuit avec
cold start de plusieurs secondes après une période d'inactivité).
Cloudflare Workers tourne sur des isolats V8, pas de conteneur qui se met
en veille : pas de cold start, et 100 000 requêtes/jour gratuites.

## Durées de Cache

Le Worker applique des durées de cache HTTP (`Cache-Control`) et Edge Cache Cloudflare alignées sur la logique du client Android (`Repository.kt`) :
- `search/*` : 300s (5 min)
- `trending/*` : 3600s (1h)
- `movie/{id}`, `tv/{id}`, `collection/{id}` : 86400s (24h)

La durée mise en cache côté Cloudflare Edge (`cache.put()`) et celle renvoyée au client dans le header HTTP `Cache-Control` sont synchronisées via `getCacheMaxAge()`.

## Déploiement

```bash
cd proxy
npm install -g wrangler   # si pas déjà installé
wrangler login
wrangler secret put TMDB_API_KEY   # colle ta clé TMDB quand demandé
wrangler deploy
```

`wrangler deploy` affiche l'URL du Worker, du type
`https://cinelog-tmdb-proxy.<ton-sous-domaine>.workers.dev`.

Colle cette URL (avec le `/` final) dans le `.env` du projet Android :

```
TMDB_PROXY_BASE_URL=https://cinelog-tmdb-proxy.<ton-sous-domaine>.workers.dev/
```

Recompile l'app, et Search/Discover/Détail fonctionnent sans qu'un
utilisateur ait besoin de configurer quoi que ce soit.

## ⚠️ À savoir avant de déployer

Une fois l'app publiée, l'URL du Worker est **extractible de l'APK** par
n'importe qui (elle est en clair dans le bytecode). Ce n'est pas un secret
— seule la clé `TMDB_API_KEY` l'est, elle. Le Worker ci-dessus limite les
dégâts possibles avec une liste blanche de chemins (`ALLOWED_PATHS`), mais
n'importe qui trouvant l'URL peut quand même taper dessus et consommer ton
quota TMDB.

Pour limiter ça, une fois le Worker déployé, pense à activer une règle de
**rate limiting** dans le dashboard Cloudflare (Workers & Pages → ton
Worker → Settings → Rate limiting rules), par exemple "X requêtes par
minute par IP". C'est gratuit sur le plan de base et ça suffit largement
pour un usage normal côté app.
