// SwiftDrop Phase 2 - Demo Store (M6)
// A simulated merchant web shop that places orders and verifies incoming signed webhooks.

const http = require("node:http");
const crypto = require("node:crypto");

const PORT = Number(process.env.PORT || 4000);
const GATEWAY_URL = process.env.SWIFTDROP_API_URL || process.env.GATEWAY_URL || "http://gateway:8080";
let signingSecret = process.env.WEBHOOK_SECRET || process.env.WEBHOOK_SIGNING_SECRET || "";

const receivedEvents = [];

function verifySignature(signatureHeader, body, secret) {
  if (!signatureHeader || !secret) {
    return { valid: false, reason: "Missing signature header or secret" };
  }

  let timestamp = null;
  let expectedV1 = null;

  for (const part of signatureHeader.split(",")) {
    const [k, v] = part.trim().split("=", 2);
    if (k === "t") timestamp = parseInt(v, 10);
    if (k === "v1") expectedV1 = v;
  }

  if (!timestamp || !expectedV1) {
    return { valid: false, reason: "Malformed signature header format (expected t=...,v1=...)" };
  }

  // Tolerance check (300 seconds / 5 minutes)
  const now = Math.floor(Date.now() / 1000);
  if (Math.abs(now - timestamp) > 300) {
    return { valid: false, reason: `Timestamp out of tolerance (t=${timestamp}, now=${now})` };
  }

  const payloadToSign = `${timestamp}.${body}`;
  const hmac = crypto.createHmac("sha256", secret).update(payloadToSign).digest("hex");

  try {
    const match = crypto.timingSafeEqual(Buffer.from(hmac, "hex"), Buffer.from(expectedV1, "hex"));
    return { valid: match, reason: match ? "Verified HMAC-SHA256" : "Hash mismatch" };
  } catch (err) {
    return { valid: false, reason: "Invalid signature digest length" };
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  // Health check
  if (req.method === "GET" && url.pathname === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ status: "UP" }));
  }

  // API: Get received webhooks (JSON for frontend polling)
  if (req.method === "GET" && url.pathname === "/api/webhooks") {
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ secretConfigured: !!signingSecret, events: receivedEvents }));
  }

  // API: Update active signing secret
  if (req.method === "POST" && url.pathname === "/api/secret") {
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", () => {
      try {
        const data = JSON.parse(body || "{}");
        if (data.secret !== undefined) {
          signingSecret = data.secret.trim();
        }
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ ok: true, secretConfigured: !!signingSecret }));
      } catch (e) {
        res.writeHead(400, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ error: e.message }));
      }
    });
    return;
  }

  // API: Clear received webhooks
  if (req.method === "POST" && url.pathname === "/api/webhooks/clear") {
    receivedEvents.length = 0;
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ ok: true }));
  }

  // API: Place order to SwiftDrop
  if (req.method === "POST" && url.pathname === "/api/orders/place") {
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", async () => {
      try {
        const payload = JSON.parse(body || "{}");
        const apiKey = payload.apiKey || process.env.MERCHANT_API_KEY || "";
        const idempotencyKey = payload.idempotencyKey || `idem-${crypto.randomUUID()}`;

        const orderData = {
          orderRef: payload.orderRef || `ORD-${Date.now().toString(36).toUpperCase()}`,
          pickupAddress: payload.pickupAddress || "123 Galle Road, Colombo 03",
          pickupCity: payload.pickupCity || "Colombo",
          pickupLat: payload.pickupLat || 6.9015,
          pickupLng: payload.pickupLng || 79.8540,
          dropoffAddress: payload.dropoffAddress || "45 Peradeniya Road, Kandy",
          dropoffCity: payload.dropoffCity || "Kandy",
          dropoffLat: payload.dropoffLat || 7.2906,
          dropoffLng: payload.dropoffLng || 80.6337,
          recipientName: payload.recipientName || "Nimal Perera",
          recipientPhone: payload.recipientPhone || "0771234567",
          weightKg: payload.weightKg || 2.0,
          size: payload.size || "MEDIUM",
          serviceType: payload.serviceType || "STANDARD"
        };

        const headers = {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey
        };
        if (apiKey) {
          headers["X-API-Key"] = apiKey;
        }

        const gatewayResponse = await fetch(`${GATEWAY_URL}/api/v1/merchant/orders`, {
          method: "POST",
          headers,
          body: JSON.stringify(orderData)
        });

        const respText = await gatewayResponse.text();
        res.writeHead(gatewayResponse.status, { "Content-Type": "application/json" });
        res.end(respText);
      } catch (err) {
        res.writeHead(502, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ error: `Failed to connect to gateway at ${GATEWAY_URL}: ${err.message}` }));
      }
    });
    return;
  }

  // Webhook Receiver: POST /webhooks/swiftdrop
  if (req.method === "POST" && url.pathname === "/webhooks/swiftdrop") {
    let rawBody = "";
    req.on("data", chunk => {
      rawBody += chunk;
      if (rawBody.length > 1_000_000) req.destroy();
    });

    req.on("end", () => {
      const eventId = req.headers["x-swiftdrop-event-id"] || "(none)";
      const signature = req.headers["x-swiftdrop-signature"] || "";
      let parsedPayload = null;
      try {
        parsedPayload = JSON.parse(rawBody);
      } catch (e) {
        parsedPayload = rawBody;
      }

      const verification = verifySignature(signature, rawBody, signingSecret);

      const record = {
        id: crypto.randomUUID(),
        eventId,
        signature,
        verified: verification.valid,
        verifyReason: verification.reason,
        receivedAt: new Date().toISOString(),
        payload: parsedPayload
      };

      receivedEvents.unshift(record);
      if (receivedEvents.length > 100) receivedEvents.pop();

      console.log(`[Webhook] Event ${eventId} - Verified: ${record.verified} (${record.verifyReason})`);

      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ received: true, verified: record.verified }));
    });
    return;
  }

  // Home Page UI
  if (req.method === "GET" && (url.pathname === "/" || url.pathname === "/index.html")) {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    res.end(renderDashboardHtml());
    return;
  }

  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ error: "Not Found" }));
});

function renderDashboardHtml() {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Urban Vogue - SwiftDrop Demo Store</title>
  <style>
    :root {
      --bg: #0f172a;
      --card: #1e293b;
      --border: #334155;
      --accent: #f97316;
      --accent-hover: #ea580c;
      --text: #f8fafc;
      --muted: #94a3b8;
      --green: #22c55e;
      --red: #ef4444;
      --blue: #38bdf8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: ui-sans-serif, system-ui, sans-serif; }
    body { background: var(--bg); color: var(--text); padding: 24px; min-height: 100vh; }
    .header { display: flex; align-items: center; justify-content: space-between; border-b: 1px solid var(--border); padding-bottom: 16px; margin-bottom: 24px; }
    .brand { display: flex; align-items: center; gap: 12px; }
    .badge { font-size: 11px; font-weight: 700; text-transform: uppercase; padding: 4px 10px; border-radius: 999px; background: rgba(249, 115, 22, 0.15); color: var(--accent); border: 1px solid var(--accent); }
    .grid { display: grid; grid-template-columns: 1fr 1.2fr; gap: 24px; }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
    .card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 20px; }
    h2 { font-size: 18px; margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; }
    .form-group { margin-bottom: 14px; }
    label { display: block; font-size: 12px; font-weight: 600; color: var(--muted); margin-bottom: 6px; }
    input, select { width: 100%; background: #0f172a; border: 1px solid var(--border); border-radius: 6px; padding: 10px 12px; color: var(--text); font-size: 14px; }
    input:focus, select:focus { outline: none; border-color: var(--accent); }
    .btn { background: var(--accent); color: white; border: none; border-radius: 6px; padding: 10px 16px; font-weight: 600; cursor: pointer; transition: 0.15s; font-size: 14px; }
    .btn:hover { background: var(--accent-hover); }
    .btn-secondary { background: #334155; color: var(--text); }
    .btn-secondary:hover { background: #475569; }
    .result-box { margin-top: 16px; padding: 14px; border-radius: 8px; background: #0b1324; border: 1px solid var(--border); font-size: 13px; font-family: monospace; white-space: pre-wrap; display: none; }
    .webhook-item { background: #0f172a; border: 1px solid var(--border); border-radius: 8px; padding: 14px; margin-bottom: 12px; }
    .webhook-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 12px; }
    .status-pill { font-size: 11px; padding: 2px 8px; border-radius: 4px; font-weight: bold; }
    .status-valid { background: rgba(34, 197, 94, 0.2); color: var(--green); border: 1px solid var(--green); }
    .status-invalid { background: rgba(239, 68, 68, 0.2); color: var(--red); border: 1px solid var(--red); }
    .status-unverified { background: rgba(148, 163, 184, 0.2); color: var(--muted); }
    .code-snippet { background: #020617; padding: 10px; border-radius: 6px; font-family: monospace; font-size: 12px; overflow-x: auto; color: #38bdf8; margin-top: 6px; }
  </style>
</head>
<body>
  <div class="header">
    <div class="brand">
      <h1 style="font-size: 22px;">🛍️ Urban Vogue Demo Store</h1>
      <span class="badge">Member 6 Simulator</span>
    </div>
    <div style="font-size: 13px; color: var(--muted);">
      SwiftDrop Gateway: <code style="color: var(--blue);">${GATEWAY_URL}</code>
    </div>
  </div>

  <div class="grid">
    <!-- Place Order Panel -->
    <div class="card">
      <h2>📦 Place Test Merchant Order</h2>
      <div class="form-group">
        <label>Merchant API Key (from Merchant Dashboard)</label>
        <input type="text" id="apiKey" placeholder="e.g. sd_live_..." value="">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
        <div class="form-group">
          <label>Pickup City</label>
          <input type="text" id="pickupCity" value="Colombo">
        </div>
        <div class="form-group">
          <label>Dropoff City</label>
          <input type="text" id="dropoffCity" value="Kandy">
        </div>
      </div>
      <div class="form-group">
        <label>Recipient Name</label>
        <input type="text" id="recipientName" value="Nimal Perera">
      </div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
        <div class="form-group">
          <label>Weight (kg)</label>
          <input type="number" step="0.5" id="weightKg" value="2.0">
        </div>
        <div class="form-group">
          <label>Service Type</label>
          <select id="serviceType">
            <option value="STANDARD">Standard</option>
            <option value="EXPRESS">Express</option>
          </select>
        </div>
      </div>
      <div class="form-group">
        <label>Order Reference</label>
        <input type="text" id="orderRef" value="ORD-${Date.now().toString(36).toUpperCase()}">
      </div>
      <button class="btn" style="width: 100%;" onclick="placeOrder()">🚀 Place Order via SwiftDrop API</button>
      <div id="orderResult" class="result-box"></div>
    </div>

    <!-- Webhook Receiver Panel -->
    <div class="card">
      <h2>
        <span>🔔 Live Webhook Feed</span>
        <button class="btn btn-secondary" style="font-size: 12px; padding: 4px 10px;" onclick="clearWebhooks()">Clear</button>
      </h2>
      <div style="background: #0b1324; padding: 12px; border-radius: 8px; border: 1px solid var(--border); margin-bottom: 16px;">
        <div style="font-size: 12px; color: var(--muted); margin-bottom: 4px;">Registered Webhook URL to enter in SwiftDrop:</div>
        <div style="font-family: monospace; color: var(--accent); font-weight: bold; font-size: 13px;">http://demo-store:4000/webhooks/swiftdrop</div>
        <div style="margin-top: 10px; display: flex; gap: 8px;">
          <input type="text" id="signingSecret" placeholder="Paste Webhook Signing Secret (whsec_...)" style="font-size: 12px; padding: 6px 10px;">
          <button class="btn btn-secondary" style="font-size: 12px; white-space: nowrap;" onclick="saveSecret()">Save Secret</button>
        </div>
      </div>

      <div id="webhookContainer">
        <div style="color: var(--muted); font-size: 13px; text-align: center; padding: 24px;">Listening for webhooks on <code>/webhooks/swiftdrop</code>...</div>
      </div>
    </div>
  </div>

  <script>
    async function placeOrder() {
      const btn = event.target;
      btn.disabled = true;
      btn.innerText = "Placing order...";
      const resBox = document.getElementById("orderResult");
      resBox.style.display = "block";
      resBox.innerText = "Sending request to SwiftDrop API...";

      try {
        const res = await fetch("/api/orders/place", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            apiKey: document.getElementById("apiKey").value,
            pickupCity: document.getElementById("pickupCity").value,
            dropoffCity: document.getElementById("dropoffCity").value,
            recipientName: document.getElementById("recipientName").value,
            weightKg: parseFloat(document.getElementById("weightKg").value),
            serviceType: document.getElementById("serviceType").value,
            orderRef: document.getElementById("orderRef").value
          })
        });

        const data = await res.json();
        resBox.innerText = "HTTP " + res.status + " " + res.statusText + "\\n" + JSON.stringify(data, null, 2);
        if (res.ok) {
          // Generate new order ref for next time
          document.getElementById("orderRef").value = "ORD-" + Math.random().toString(36).substring(2, 8).toUpperCase();
        }
      } catch (e) {
        resBox.innerText = "Error: " + e.message;
      } finally {
        btn.disabled = false;
        btn.innerText = "🚀 Place Order via SwiftDrop API";
      }
    }

    async function saveSecret() {
      const secret = document.getElementById("signingSecret").value;
      await fetch("/api/secret", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ secret })
      });
      alert("Secret updated for signature verification!");
    }

    async function clearWebhooks() {
      await fetch("/api/webhooks/clear", { method: "POST" });
      pollWebhooks();
    }

    async function pollWebhooks() {
      try {
        const res = await fetch("/api/webhooks");
        const data = await res.json();
        const container = document.getElementById("webhookContainer");

        if (!data.events || data.events.length === 0) {
          container.innerHTML = '<div style="color: var(--muted); font-size: 13px; text-align: center; padding: 24px;">Waiting for parcel status events...</div>';
          return;
        }

        container.innerHTML = data.events.map(ev => {
          const badgeClass = ev.verified ? "status-valid" : (ev.verifyReason.includes("Missing") ? "status-unverified" : "status-invalid");
          const badgeText = ev.verified ? "✓ SIGNATURE VALID" : (ev.verifyReason.includes("Missing") ? "UNVERIFIED (NO SECRET)" : "✗ INVALID SIGNATURE");
          const time = new Date(ev.receivedAt).toLocaleTimeString();
          return \`
            <div class="webhook-item">
              <div class="webhook-head">
                <span style="font-weight: 600;">Event \${ev.eventId}</span>
                <span class="status-pill \${badgeClass}">\${badgeText}</span>
              </div>
              <div style="font-size: 12px; color: var(--muted); margin-bottom: 6px;">
                Received at: \${time} | \${ev.verifyReason}
              </div>
              <div class="code-snippet">\${JSON.stringify(ev.payload, null, 2)}</div>
            </div>
          \`;
        }).join("");
      } catch (err) {
        console.error("Poll error:", err);
      }
    }

    setInterval(pollWebhooks, 2000);
    pollWebhooks();
  </script>
</body>
</html>`;
}

server.listen(PORT, () => {
  console.log(`Demo Store listening on port ${PORT}`);
  console.log(`SwiftDrop API Gateway: ${GATEWAY_URL}`);
});

process.on("SIGTERM", () => server.close(() => process.exit(0)));
