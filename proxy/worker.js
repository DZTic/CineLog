/**
 * CineLog TMDB proxy — Cloudflare Worker
 *
 * Forwards a small allow-listed set of TMDB v3 endpoints to
 * api.themoviedb.org, injecting the real TMDB API key from a Worker
 * secret so it never ships inside the Android app. Any `api_key` query
 * param sent by the client is ignored and overwritten server-side.
 *
 * This lets CineLog work out of the box for every user without asking
 * them to create a TMDB developer account first. Users who want their
 * own dedicated quota can still paste a personal key in Settings — the
 * app then calls TMDB directly and skips this proxy entirely.
 *
 * See README.md in this folder for deployment steps.
 */

const TMDB_BASE = "https://api.themoviedb.org/3";

// Only these path *shapes* are forwarded. Anything else is rejected so a
// Worker URL bundled inside a public APK (and therefore extractable by
// anyone) can't be used as an open proxy to arbitrary TMDB endpoints or
// to hammer unrelated ones.
const ALLOWED_PATHS = [
  /^\/search\/movie$/,
  /^\/search\/tv$/,
  /^\/movie\/\d+$/,
  /^\/tv\/\d+$/,
  /^\/collection\/\d+$/,
  /^\/trending\/movie\/week$/,
  /^\/trending\/tv\/week$/,
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (!ALLOWED_PATHS.some((re) => re.test(url.pathname))) {
      return new Response("Not found", { status: 404 });
    }

    if (!env.TMDB_API_KEY) {
      return new Response(
        "Proxy misconfigured: missing TMDB_API_KEY secret (see README.md)",
        { status: 500 }
      );
    }

    const upstream = new URL(TMDB_BASE + url.pathname);
    // Copy through every query param the client sent (query, language,
    // append_to_response, ...) except api_key, which the proxy always
    // controls itself — the app only ever sends a harmless placeholder.
    for (const [key, value] of url.searchParams) {
      if (key !== "api_key") upstream.searchParams.set(key, value);
    }
    upstream.searchParams.set("api_key", env.TMDB_API_KEY);

    const upstreamResponse = await fetch(upstream.toString(), {
      headers: { Accept: "application/json" },
    });

    const headers = new Headers(upstreamResponse.headers);
    // Short shared cache: trending/search results don't need to be
    // realtime, and this takes load off both the Worker and TMDB's quota
    // when many users search for the same popular title.
    headers.set("Cache-Control", "public, max-age=300");

    return new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      headers,
    });
  },
};
