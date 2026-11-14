const { createHmac, timingSafeEqual } = require("node:crypto");

function sign(secret, timestamp, body) {
  return (
    "t=" +
    timestamp +
    ",v1=" +
    createHmac("sha256", secret)
      .update(String(timestamp) + ".")
      .update(body)
      .digest("hex")
  );
}
function verify(secret, header, body, now = Math.floor(Date.now() / 1000)) {
  if (!secret || typeof header !== "string") return false;
  const match = /^t=([0-9]{1,12}),v1=([a-f0-9]{64})$/.exec(header);
  if (!match || Math.abs(now - Number(match[1])) > 300) return false;
  const expected = sign(secret, match[1], body).split("v1=")[1];
  return timingSafeEqual(
    Buffer.from(expected, "hex"),
    Buffer.from(match[2], "hex"),
  );
}
module.exports = { sign, verify };
