import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "./api";

const json = (status: number, body?: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

function mockFetch(handler: (url: string) => Response | Promise<Response>) {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const fn = vi.fn((input: RequestInfo | URL, init?: RequestInit) => Promise.resolve(handler(String(input))));
  vi.stubGlobal("fetch", fn);
  return fn;
}

const urls = (fn: ReturnType<typeof mockFetch>) => fn.mock.calls.map(([u]) => String(u));

afterEach(() => vi.unstubAllGlobals());

describe("api client", () => {
  it("refreshes once on 401 and retries the request", async () => {
    let meCalls = 0;
    const fetch = mockFetch((url) => {
      if (url.endsWith("/auth/refresh")) return json(200, {});
      return ++meCalls === 1 ? json(401) : json(200, { id: "u1" });
    });

    await expect(api("/auth/me")).resolves.toEqual({ id: "u1" });
    expect(urls(fetch)).toEqual(["/api/v1/auth/me", "/api/v1/auth/refresh", "/api/v1/auth/me"]);
  });

  it("gives up with 401 when the refresh fails", async () => {
    const fetch = mockFetch(() => json(401, { detail: "Session expired, please log in again" }));

    await expect(api("/auth/me")).rejects.toMatchObject({ status: 401 });
    expect(urls(fetch)).toEqual(["/api/v1/auth/me", "/api/v1/auth/refresh"]);
  });

  it("shares one refresh between concurrent 401s", async () => {
    const seen = new Map<string, number>();
    const fetch = mockFetch((url) => {
      const n = (seen.get(url) ?? 0) + 1;
      seen.set(url, n);
      if (url.endsWith("/auth/refresh")) return new Promise((r) => setTimeout(() => r(json(200, {})), 10));
      return n === 1 ? json(401) : json(200, { url });
    });

    await Promise.all([api("/parcels/ping"), api("/dispatch/ping")]);
    expect(urls(fetch).filter((u) => u.endsWith("/auth/refresh"))).toHaveLength(1);
  });

  it("does not refresh when login itself returns 401", async () => {
    const fetch = mockFetch(() => json(401, { detail: "Invalid email or password" }));

    await expect(api("/auth/login", { method: "POST", json: { email: "a@b.lk", password: "x" } })).rejects.toThrow(
      "Invalid email or password",
    );
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("maps ProblemDetail field errors", async () => {
    mockFetch(() =>
      json(400, {
        detail: "Validation failed",
        errors: [
          { field: "email", message: "must be a well-formed email address" },
          { field: "phone", message: "must be a Sri Lankan number" },
        ],
      }),
    );

    const error = await api("/auth/signup", { method: "POST", json: {} }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.fieldErrors).toEqual({
      email: "must be a well-formed email address",
      phone: "must be a Sri Lankan number",
    });
  });

  it("sends JSON bodies and handles 204", async () => {
    const fetch = mockFetch(() => new Response(null, { status: 204 }));

    await expect(api("/auth/logout", { method: "POST", json: { a: 1 } })).resolves.toBeUndefined();
    const init = fetch.mock.calls[0][1] as RequestInit;
    expect(init.body).toBe('{"a":1}');
    expect(init.credentials).toBe("same-origin");
  });
});
