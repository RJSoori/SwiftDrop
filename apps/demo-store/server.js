// Placeholder Demo Store (M6 replaces this). It only proves the container runs and logs incoming webhooks,
// so the compose wiring (port, env, network name) can be tested before the real app exists.
const http = require("node:http");

const PORT = Number(process.env.PORT || 4000);
const received = [];

const server = http.createServer((req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ status: "UP" }));
  }

  if (req.method === "POST" && req.url === "/webhooks/swiftdrop") {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 1_000_000) req.destroy(); // a webhook is never this big
    });
    req.on("end", () => {
      // Signature checking is M6's job; the placeholder only records the event id.
      received.unshift({ eventId: req.headers["x-swiftdrop-event-id"] || null, at: new Date().toISOString() });
      received.length = Math.min(received.length, 50);
      res.writeHead(204);
      res.end();
    });
    return;
  }

  if (req.method === "GET" && req.url === "/") {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    const rows = received.map((e) => `<li>${e.at} - ${e.eventId ?? "(no event id)"}</li>`).join("");
    return res.end(`<!doctype html><title>Demo Store</title><h1>Demo Store (placeholder)</h1>
<p>SwiftDrop API: ${process.env.SWIFTDROP_API_URL || "not set"}</p><h2>Webhooks received</h2><ul>${rows || "<li>none yet</li>"}</ul>`);
  }

  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ error: "not found" }));
});

server.listen(PORT, () => console.log(`demo-store listening on ${PORT}`));
process.on("SIGTERM", () => server.close(() => process.exit(0)));
