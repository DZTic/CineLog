// CineLog TMDB proxy.
// Forwards GET requests to the TMDB API while injecting a server-side API key,
// so the app works out of the box without every user configuring their own key.
// Zero dependencies, Node >= 18.

const http = require("http");

const TMDB_API_KEY = process.env.TMDB_API_KEY;
const PORT = process.env.PORT || 3000;

if (!TMDB_API_KEY) {
  console.error("TMDB_API_KEY environment variable is required");
  process.exit(1);
}

const server = http.createServer(async (req, res) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  if (req.method !== "GET") {
    res.writeHead(405);
    res.end();
    return;
  }
  try {
    const url = new URL(req.url, "http://localhost");
    // The proxy owns the key: any client-provided api_key is overridden.
    url.searchParams.set("api_key", TMDB_API_KEY);
    const upstream = await fetch(`https://api.themoviedb.org/3${url.pathname}${url.search}`);
    res.writeHead(upstream.status, {
      "Content-Type": upstream.headers.get("content-type") || "application/json",
    });
    res.end(Buffer.from(await upstream.arrayBuffer()));
  } catch (err) {
    console.error("Proxy error:", err);
    res.writeHead(502, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "proxy_error" }));
  }
});

server.listen(PORT, () => console.log(`TMDB proxy listening on :${PORT}`));
