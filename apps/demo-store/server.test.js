const { test } = require("node:test");
const assert = require("node:assert/strict");
const { createServer } = require("./server");
const { sign } = require("./signature");
async function start(t, options) {
  const server = createServer(options);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise((resolve) => server.close(resolve)));
  return "http://127.0.0.1:" + server.address().port;
}
test("order requests keep the API key server-side and forward the idempotency key", async (t) => {
  let sent;
  const base = await start(t, {
    apiKey: "private-key",
    fetchImpl: async (url, options) => {
      sent = { url, ...options };
      return new Response(
        JSON.stringify({ trackingNo: "SD-TEST", feeLkr: 510 }),
        { status: 201 },
      );
    },
  });
  const response = await fetch(base + "/api/orders", {
    method: "POST",
    headers: { "Idempotency-Key": "order-123" },
  });
  assert.equal(response.status, 201);
  assert.equal(sent.headers["X-API-Key"], "private-key");
  assert.equal(sent.headers["Idempotency-Key"], "order-123");
  assert.equal(JSON.parse(sent.body).orderRef, "DEMO-order-123");
  assert.equal(
    (await (await fetch(base + "/api/config")).text()).includes("private-key"),
    false,
  );
});
test("receiver rejects invalid signatures, deduplicates valid events, and matches event IDs", async (t) => {
  const base = await start(t, { webhookSecret: "secret" });
  const body = JSON.stringify({
    eventId: "event-1",
    data: { toStatus: "DELIVERED" },
  });
  const headers = {
    "Content-Type": "application/json",
    "X-SwiftDrop-Event-Id": "event-1",
    "X-SwiftDrop-Signature": sign(
      "secret",
      Math.floor(Date.now() / 1000),
      Buffer.from(body),
    ),
  };
  assert.equal(
    (
      await fetch(base + "/webhooks/swiftdrop", {
        method: "POST",
        body,
        headers: { ...headers, "X-SwiftDrop-Signature": "bad" },
      })
    ).status,
    401,
  );
  for (let i = 0; i < 2; i++)
    assert.equal(
      (
        await fetch(base + "/webhooks/swiftdrop", {
          method: "POST",
          body,
          headers,
        })
      ).status,
      204,
    );
  assert.equal((await (await fetch(base + "/api/events")).json()).length, 1);
  assert.equal(
    (
      await fetch(base + "/webhooks/swiftdrop", {
        method: "POST",
        body,
        headers: { ...headers, "X-SwiftDrop-Event-Id": "other" },
      })
    ).status,
    400,
  );
});
test("missing configuration and cross-origin order creation fail safely", async (t) => {
  const base = await start(t, {});
  assert.equal(
    (
      await fetch(base + "/api/orders", {
        method: "POST",
        headers: { Origin: "https://other.example" },
      })
    ).status,
    403,
  );
  assert.equal(
    (await fetch(base + "/api/orders", { method: "POST" })).status,
    503,
  );
  assert.equal(
    (await fetch(base + "/webhooks/swiftdrop", { method: "POST", body: "{}" }))
      .status,
    503,
  );
});
