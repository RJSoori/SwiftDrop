const CACHE_NAME = "swiftdrop-static-v2";
const PRECACHE_URLS = ["/icons/courier-192.svg", "/icons/courier-512.svg"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith("swiftdrop-static-") && key !== CACHE_NAME)
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  const isStaticAsset = url.origin === self.location.origin
    && request.method === "GET"
    && (url.pathname.startsWith("/_next/static/") || url.pathname.startsWith("/icons/"));

  if (!isStaticAsset) return;

  event.respondWith(
    (async () => {
      const cached = await caches.match(request);
      if (cached) return cached;

      const response = await fetch(request);
      if (response.ok) {
        const responseForCache = response.clone();
        const cacheWrite = caches.open(CACHE_NAME).then((cache) => cache.put(request, responseForCache));
        event.waitUntil(
          cacheWrite.catch((error) => {
            console.error("Could not cache a static asset.", error);
          }),
        );
      }
      return response;
    })(),
  );
});
