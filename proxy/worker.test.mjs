import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import worker from "./worker.js";

describe("Cloudflare Worker TMDB Proxy", () => {
  const originalFetch = globalThis.fetch;
  const originalCaches = globalThis.caches;

  let mockCacheStore;
  let mockCache;

  beforeEach(() => {
    mockCacheStore = new Map();
    mockCache = {
      match: async (req) => {
        const url = typeof req === "string" ? req : req.url;
        const res = mockCacheStore.get(url);
        return res ? res.clone() : undefined;
      },
      put: async (req, res) => {
        const url = typeof req === "string" ? req : req.url;
        mockCacheStore.set(url, res.clone());
      },
    };
    globalThis.caches = { default: mockCache };
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    globalThis.caches = originalCaches;
  });

  it("should reject non-GET requests with 405 Method not allowed", async () => {
    const request = new Request("http://localhost:8787/search/movie", {
      method: "POST",
    });
    const env = { TMDB_API_KEY: "dummy-key" };
    const response = await worker.fetch(request, env);

    assert.equal(response.status, 405);
    assert.equal(response.headers.get("Allow"), "GET");
  });

  it("should reject disallowed paths with 404", async () => {
    const request = new Request("http://localhost:8787/unauthorized/path");
    const env = { TMDB_API_KEY: "dummy-key" };
    const response = await worker.fetch(request, env);

    assert.equal(response.status, 404);
  });

  it("should return 500 if TMDB_API_KEY is missing", async () => {
    const request = new Request("http://localhost:8787/search/movie?query=Avatar");
    const env = {};
    const response = await worker.fetch(request, env);

    assert.equal(response.status, 500);
    const body = await response.text();
    assert.match(body, /missing TMDB_API_KEY/);
  });

  it("should reject page numbers out of bounds (0 or > 5) with 404", async () => {
    const env = { TMDB_API_KEY: "dummy-key" };

    const res0 = await worker.fetch(
      new Request("http://localhost:8787/search/movie?query=Avatar&page=0"),
      env
    );
    assert.equal(res0.status, 404);

    const res6 = await worker.fetch(
      new Request("http://localhost:8787/search/movie?query=Avatar&page=6"),
      env
    );
    assert.equal(res6.status, 404);
  });

  it("should reject disallowed append_to_response values with 404", async () => {
    const env = { TMDB_API_KEY: "dummy-key" };
    const res = await worker.fetch(
      new Request("http://localhost:8787/movie/550?append_to_response=unknown_dangerous_resource"),
      env
    );
    assert.equal(res.status, 404);
  });

  it("should call ctx.waitUntil with cache.put promise on successful upstream response", async () => {
    const env = { TMDB_API_KEY: "test-secret-key" };
    const request = new Request("http://localhost:8787/movie/550?language=fr-FR");

    let upstreamRequestedUrl = null;
    globalThis.fetch = async (url, options) => {
      upstreamRequestedUrl = url;
      return new Response(JSON.stringify({ id: 550, title: "Fight Club" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const waitUntilPromises = [];
    const ctx = {
      waitUntil: (promise) => {
        waitUntilPromises.push(promise);
      },
    };

    const response = await worker.fetch(request, env, ctx);

    assert.equal(response.status, 200);
    const data = await response.json();
    assert.equal(data.title, "Fight Club");

    // Verify upstream URL had API key injected and allowed params forwarded
    assert.ok(upstreamRequestedUrl);
    const upstreamUrlObj = new URL(upstreamRequestedUrl);
    assert.equal(upstreamUrlObj.searchParams.get("api_key"), "test-secret-key");
    assert.equal(upstreamUrlObj.searchParams.get("language"), "fr-FR");

    // Verify ctx.waitUntil was called
    assert.equal(waitUntilPromises.length, 1);
    await Promise.all(waitUntilPromises);

    // Verify item is now in edge cache
    assert.equal(mockCacheStore.size, 1);
    const cachedEntry = mockCacheStore.get(upstreamRequestedUrl);
    assert.ok(cachedEntry);
    assert.equal(cachedEntry.headers.get("Cache-Control"), "public, max-age=86400");
  });

  it("should return cached response on cache hit without upstream fetch", async () => {
    const env = { TMDB_API_KEY: "test-secret-key" };
    const request = new Request("http://localhost:8787/trending/movie/week");

    let upstreamFetchCalled = false;
    globalThis.fetch = async () => {
      upstreamFetchCalled = true;
      return new Response("{}", { status: 200 });
    };

    const cachedUpstreamUrl = "https://api.themoviedb.org/3/trending/movie/week?api_key=test-secret-key";
    mockCacheStore.set(
      cachedUpstreamUrl,
      new Response(JSON.stringify({ results: [{ id: 1, title: "Cached Movie" }] }), {
        status: 200,
        headers: {
          "Content-Type": "application/json",
          "Cache-Control": "public, max-age=3600",
        },
      })
    );

    const ctx = { waitUntil: () => {} };
    const response = await worker.fetch(request, env, ctx);

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.results[0].title, "Cached Movie");
    assert.equal(upstreamFetchCalled, false);
    assert.equal(response.headers.get("Cache-Control"), "public, max-age=3600");
  });

  it("should handle execution gracefully when ctx is not provided (defensive fallback)", async () => {
    const env = { TMDB_API_KEY: "test-secret-key" };
    const request = new Request("http://localhost:8787/search/tv?query=Breaking");

    globalThis.fetch = async () => {
      return new Response(JSON.stringify({ results: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    // Calling without ctx
    const response = await worker.fetch(request, env);
    assert.equal(response.status, 200);
    assert.equal(mockCacheStore.size, 1);
  });

  it("should not cache upstream error responses (e.g. 404 or 500)", async () => {
    const env = { TMDB_API_KEY: "test-secret-key" };
    const request = new Request("http://localhost:8787/movie/9999999");

    globalThis.fetch = async () => {
      return new Response(JSON.stringify({ status_message: "Not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      });
    };

    const waitUntilPromises = [];
    const ctx = {
      waitUntil: (promise) => waitUntilPromises.push(promise),
    };

    const response = await worker.fetch(request, env, ctx);
    assert.equal(response.status, 404);
    assert.equal(waitUntilPromises.length, 0);
    assert.equal(mockCacheStore.size, 0);
  });
});
