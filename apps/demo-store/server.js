const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { verify } = require("./signature");
const page = fs.readFileSync(path.join(__dirname, "index.html"));

async function readBody(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 65536)
      throw Object.assign(new Error("Body too large"), { status: 413 });
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}
function send(res, status, data) {
  res.writeHead(status, {
    "Content-Type": "application/json",
    "Cache-Control": "no-store",
  });
  res.end(JSON.stringify(data));
}
function createServer(options = {}) {
  const apiUrl =
    options.apiUrl ?? process.env.SWIFTDROP_API_URL ?? "http://localhost:8080";
  const apiKey = options.apiKey ?? process.env.DEMO_STORE_API_KEY ?? "";
  const secret =
    options.webhookSecret ?? process.env.DEMO_STORE_WEBHOOK_SECRET ?? "";
  const fetchApi = options.fetchImpl ?? fetch;
  const received = [];
  const seen = new Set();
  return http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, "http://localhost");
      if (req.method === "GET" && url.pathname === "/health")
        return send(res, 200, { status: "UP" });
      if (req.method === "GET" && url.pathname === "/") {
        res.writeHead(200, {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "no-store",
        });
        return res.end(page);
      }
      if (req.method === "GET" && url.pathname === "/api/config")
        return send(res, 200, {
          apiKeyConfigured: !!apiKey,
          webhookSecretConfigured: !!secret,
        });
      if (req.method === "GET" && url.pathname === "/api/events")
        return send(res, 200, received);
      if (req.method === "POST" && url.pathname === "/webhooks/swiftdrop") {
        if (!secret)
          return send(res, 503, {
            error: "Webhook signing secret is not configured",
          });
        const body = await readBody(req);
        if (!verify(secret, req.headers["x-swiftdrop-signature"], body))
          return send(res, 401, {
            error: "Invalid or expired webhook signature",
          });
        let event;
        try {
          event = JSON.parse(body.toString("utf8"));
        } catch {
          return send(res, 400, { error: "Invalid event JSON" });
        }
        const eventId = req.headers["x-swiftdrop-event-id"];
        if (!eventId || event.eventId !== eventId)
          return send(res, 400, { error: "Event ID mismatch" });
        if (!seen.has(eventId)) {
          seen.add(eventId);
          received.unshift({ eventId, at: new Date().toISOString(), event });
          if (received.length > 100) seen.delete(received.pop().eventId);
        }
        res.writeHead(204);
        return res.end();
      }
      if (req.method === "POST" && url.pathname === "/api/orders") {
        if (
          req.headers.origin &&
          new URL(req.headers.origin).host !== req.headers.host
        )
          return send(res, 403, {
            error: "Use the Demo Store page to place orders",
          });
        if (!apiKey)
          return send(res, 503, {
            error:
              "Set DEMO_STORE_API_KEY in infra/compose/.env and recreate demo-store",
          });
        const key = req.headers["idempotency-key"];
        if (typeof key !== "string" || !/^[a-zA-Z0-9-]{1,100}$/.test(key))
          return send(res, 400, {
            error: "A valid Idempotency-Key is required",
          });
        const order = {
          orderRef: "DEMO-" + key,
          pickupAddress: "12 Galle Road",
          pickupCity: "Colombo",
          pickupLat: 6.9271,
          pickupLng: 79.8612,
          dropoffAddress: "24 Temple Road",
          dropoffCity: "Kandy",
          dropoffLat: 7.2906,
          dropoffLng: 80.6337,
          recipientName: "Demo Customer",
          recipientPhone: "+94771234567",
          weightKg: 2,
          size: "MEDIUM",
          serviceType: "STANDARD",
        };
        const upstream = await fetchApi(apiUrl + "/api/v1/merchant/orders", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-API-Key": apiKey,
            "Idempotency-Key": key,
          },
          body: JSON.stringify(order),
          signal: AbortSignal.timeout(15000),
        });
        const data = await upstream.json();
        return send(res, upstream.status, data);
      }
      return send(res, 404, { error: "Not found" });
    } catch (error) {
      return send(res, error.status ?? 502, {
        error:
          error.status === 413
            ? "Body too large"
            : "Request failed; retry using the same order key",
      });
    }
  });
}
if (require.main === module) {
  const server = createServer();
  server.requestTimeout = 20000;
  server.headersTimeout = 10000;
  server.listen(Number(process.env.PORT || 4000), () =>
    console.log("Demo Store listening on port " + (process.env.PORT || 4000)),
  );
  process.on("SIGTERM", () => server.close(() => process.exit(0)));
}
module.exports = { createServer };
