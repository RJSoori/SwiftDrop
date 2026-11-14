import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { describe, expect, it, vi } from "vitest";

type FetchEventLike = {
  request: Request;
  respondWith: (response: Promise<Response>) => void;
  waitUntil: (promise: Promise<unknown>) => void;
};

describe("service worker static asset caching", () => {
  it("clones the response before the asynchronous cache open completes", async () => {
    const source = readFileSync(new URL("../../public/sw.js", import.meta.url), "utf8");
    const listeners = new Map<string, (event: FetchEventLike) => void>();
    const worker = {
      location: { origin: "https://swiftdrop.test" },
      addEventListener: (type: string, listener: (event: FetchEventLike) => void) => {
        listeners.set(type, listener);
      },
    };
    const cache = {
      put: vi.fn(async (_request: Request, response: Response) => response.text()),
    };

    let openCache!: (value: typeof cache) => void;
    const cacheOpening = new Promise<typeof cache>((resolve) => {
      openCache = resolve;
    });
    const caches = {
      match: vi.fn(async () => undefined),
      open: vi.fn(() => cacheOpening),
    };
    const fetch = vi.fn(async () => new Response("asset contents"));
    const waitUntilPromises: Promise<unknown>[] = [];
    let responsePromise!: Promise<Response>;

    runInNewContext(source, { self: worker, caches, fetch, URL });

    const onFetch = listeners.get("fetch");
    expect(onFetch).toBeDefined();
    onFetch!({
      request: new Request("https://swiftdrop.test/_next/static/chunk.js"),
      respondWith: (response) => {
        responsePromise = response;
      },
      waitUntil: (promise) => {
        waitUntilPromises.push(promise);
      },
    });

    const response = await responsePromise;
    expect(await response.text()).toBe("asset contents");
    openCache(cache);
    await Promise.all(waitUntilPromises);

    expect(cache.put).toHaveBeenCalledOnce();
    await expect(cache.put.mock.results[0]?.value).resolves.toBe("asset contents");
  });
});
