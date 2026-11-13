import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import { chatErrorMessage, chatRequestSchema, pendingLabel, sendChat } from "./assistant";

const SESSION = "7b2f410c-68e9-4c65-99ba-08a60b7678f7";

function mockFetch(status: number, body?: unknown) {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const fn = vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
    Promise.resolve(
      new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
      }),
    ),
  );
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => vi.unstubAllGlobals());

describe("sendChat", () => {
  it("posts the trimmed message and returns the parsed reply", async () => {
    const fetch = mockFetch(200, { sessionId: SESSION, reply: "Your parcel is out for delivery.", toolsUsed: ["track_parcel"] });

    await expect(sendChat({ message: "  Where is SD-1001?  " })).resolves.toEqual({
      sessionId: SESSION,
      reply: "Your parcel is out for delivery.",
      toolsUsed: ["track_parcel"],
    });
    const [url, init] = fetch.mock.calls[0]!;
    expect(url).toBe("/api/v1/assistant/chat");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ message: "Where is SD-1001?" });
  });

  it("sends the session id to continue a conversation", async () => {
    const fetch = mockFetch(200, { sessionId: SESSION, reply: "LKR 530", toolsUsed: [] });

    await sendChat({ sessionId: SESSION, message: "And for 2 kg?" });
    expect(JSON.parse(String(fetch.mock.calls[0]![1]?.body))).toEqual({ sessionId: SESSION, message: "And for 2 kg?" });
  });

  it("accepts the fallback reply that has no session yet", async () => {
    mockFetch(200, { sessionId: null, reply: "Sorry, the assistant isn't available right now.", toolsUsed: [] });

    await expect(sendChat({ message: "hi" })).resolves.toMatchObject({ sessionId: null });
  });

  it("never calls the API for a blank or oversized message", async () => {
    const fetch = mockFetch(200, {});

    await expect(sendChat({ message: "   " })).rejects.toThrow();
    await expect(sendChat({ message: "x".repeat(2001) })).rejects.toThrow();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("rejects a malformed response instead of showing it", async () => {
    mockFetch(200, { reply: 42 });

    await expect(sendChat({ message: "hi" })).rejects.toThrow();
  });

  it("surfaces API errors as ApiError", async () => {
    mockFetch(403, { detail: "Access denied" });

    await expect(sendChat({ message: "hi" })).rejects.toMatchObject({ status: 403 });
  });
});

describe("chatErrorMessage", () => {
  it("explains each error state in plain words", () => {
    expect(chatErrorMessage(new ApiError(401, "x"))).toMatch(/log in again/);
    expect(chatErrorMessage(new ApiError(429, "x"))).toMatch(/wait a moment/i);
    expect(chatErrorMessage(new ApiError(503, "x"))).toMatch(/couldn't reach the assistant/);
    expect(chatErrorMessage(new TypeError("Failed to fetch"))).toMatch(/couldn't reach the assistant/);
    expect(chatErrorMessage(chatRequestSchema.safeParse({ message: "" }).error)).toBe("Type a question first");
  });
});

describe("pendingLabel", () => {
  it("guesses what the assistant is doing from the question", () => {
    expect(pendingLabel("Where is my parcel SD-1001?")).toBe("Checking your parcel…");
    expect(pendingLabel("How much to send 2 kg to Kandy?")).toBe("Working out the price…");
    expect(pendingLabel("hello")).toBe("Thinking…");
  });
});
