/**
 * CineLog TMDB proxy - Cloudflare Worker
 *
 * Forwards a small allow-listed set of TMDB v3 endpoints to
 * api.themoviedb.org, injecting the real TMDB API key from a worker
 * secret so it never ships inside the Android app. Any `api_key` query
 * param sent by the client is ignored and overwritten server-side.
 *
 * This lets CineLog work out of the box for every user without asking
 * them to create a TMDB developer account first. Users who want their
 * own dedicated quota can still paste a personal key in Settings - the
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

// Only these query params are forwarded upstream. Anything else sent by a
// client (known or invented) is dropped, so an attacker who extracts the
// worker URL from the APK can't tune requests to be heavier than what the
// app itself ever sends.
const ALLOWED_PARAMS = new Set([
  "query",
  "language",
  "page",
  "append_to_response",
]);
const MAX_PAGE = 5;

// append_to_response is restricted to the cheap sub-resources the app can
// plausibly request. Kept as a whitelist so a comma-separated abuse value
// like "credits,images,videos,reviews,keywords,..." is rejected outright.
// Extend this list if the app ever needs another sub-resource.
const ALLOWED_APPEND_TO_RESPONSE = new Set([
  "credits",
  "images",
  "videos",
  "similar",
  "recommendations",
]);

/**
 * Determines Cache-Control max-age in seconds based on the request pathname.
 * Durations match client-side Repository cache rules.
 */
function getCacheMaxAge(pathname) {
  if (pathname.startsWith("/trending/")) {
    return 3600;
  }
  if (
    pathname.startsWith("/movie/") ||
    pathname.startsWith("/tv/") ||
    pathname.startsWith("/collection/")
  ) {
    return 86400;
  }
  return 300;
}

export default {
  async fetch(request, env) {
    // Read-only proxy: anything but GET has no business here.
    if (request.method !== "GET") {
      return new Response("Method not allowed", {
        status: 405,
        headers: { Allow: "GET" },
      });
    }

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
    // Copy through allow-listed query params only. api_key is skipped and
    // always controlled server-side - the app only ever sends a harmless
    // placeholder.
    for (const [key, value] of url.searchParams) {
      if (key !== "api_key" && ALLOWED_PARAMS.has(key)) {
        upstream.searchParams.set(key, value);
      }
    }

    // Keep pagination shallow: deep paging is a classic quota drain and the
    // app never goes past the first pages.
    const page = upstream.searchParams.get("page");
    if (page !== null) {
      const pageNum = parseInt(page, 10);
      if (!Number.isFinite(pageNum) || pageNum < 1 || pageNum > MAX_PAGE) {
        return new Response("Not found", { status: 404 });
      }
    }

    // Reject aggressive append_to_response combinations.
    const atr = upstream.searchParams.get("append_to_response");
    if (atr !== null) {
      const parts = atr.split(",").map((p) => p.trim());
      if (
        parts.length === 0 ||
        parts.some((p) => !ALLOWED_APPEND_TO_RESPONSE.has(p))
      ) {
        return new Response("Not found", { status: 404 });
      }
    }

    upstream.searchParams.set("api_key", env.TMDB_API_KEY);

    // Edge cache: identical requests (popular searches, trending pages) are
    // served from Cloudflare's cache for a few minutes without touching
    // TMDB at all. This absorbs naive spam of repeated identical URLs and
    // protects the shared TMDB quota. The API key lives in the cache key
    // URL, which is fine: cached responses are only served to callers who
    // already present this exact proxied URL, and the key never appears in
    // the response body or headers.
    const maxAge = getCacheMaxAge(url.pathname);
    const cacheControlHeader = `public, max-age=${maxAge}`;
    const cache = caches.default;
    const cacheKey = new Request(upstream.toString(), { method: "GET" });
    const cached = await cache.match(cacheKey);
    let upstreamResponse;
    if (cached) {
      upstreamResponse = cached;
    } else {
      const fetched = await fetch(upstream.toString(), {
        headers: { Accept: "application/json" },
      });
      // Only cache successful responses; pass errors through uncached so a
      // transient TMDB failure isn't remembered for five minutes.
      if (fetched.ok) {
        const toCache = new Response(fetched.clone().body, {
          status: fetched.status,
          headers: {
            "Content-Type": "application/json",
            "Cache-Control": cacheControlHeader,
          },
        });
        cache.put(cacheKey, toCache);
      }
      upstreamResponse = fetched;
    }

    // Deliberately minimal response headers: don't relay TMDB's retry-after,
    // rate-limit or cf-* headers, which would leak how much of the shared
    // quota is being consumed.
    const headers = new Headers({
      "Content-Type":
        upstreamResponse.headers.get("Content-Type") ?? "application/json",
      "Cache-Control": cacheControlHeader,
    });

    return new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      headers,
    });
  },
};
