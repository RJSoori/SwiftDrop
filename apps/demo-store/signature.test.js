const { test } = require("node:test");
const assert = require("node:assert/strict");
const { sign, verify } = require("./signature");
test("signature authenticates the exact raw UTF-8 bytes", () => {
  const body = Buffer.from('{"city":"Colombo \u2603"}');
  const header = sign("secret", 1700000000, body);
  assert.equal(verify("secret", header, body, 1700000000), true);
  assert.equal(verify("wrong", header, body, 1700000000), false);
  assert.equal(verify("secret", header, Buffer.from("{}"), 1700000000), false);
});
test("stale, future, malformed, and missing signatures are rejected", () => {
  const body = Buffer.from("{}");
  const h = sign("secret", 1000, body);
  assert.equal(verify("secret", h, body, 1301), false);
  assert.equal(verify("secret", h, body, 699), false);
  for (const bad of [
    null,
    "t=1000,v1=abc",
    "t=bad,v1=" + "0".repeat(64),
    h + ",v1=extra",
  ])
    assert.equal(verify("secret", bad, body, 1000), false);
  assert.equal(verify("", h, body, 1000), false);
});
