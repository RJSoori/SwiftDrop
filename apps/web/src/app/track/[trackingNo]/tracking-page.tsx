"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowLeft, CheckCircle2, Clock, MapPin, Package, Truck } from "lucide-react";
import { Brand } from "@/components/brand";
import { Badge } from "@/components/ui/badge";
import { api, ApiError, type TrackingView } from "@/lib/api";

function formatStatus(status: string) {
  return status.replaceAll("_", " ");
}

export function TrackingPage({ trackingNo }: { trackingNo: string }) {
  const [tracking, setTracking] = useState<TrackingView | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    let active = true;
    let source: EventSource | undefined;

    api<TrackingView>(`/tracking/${encodeURIComponent(trackingNo)}`)
      .then((result) => {
        if (!active) return;
        setTracking(result);
        source = new EventSource(`/api/v1/tracking/${encodeURIComponent(trackingNo)}/stream`);
        source.addEventListener("tracking", (event) => {
          if (!active) return;
          try {
            const update = JSON.parse((event as MessageEvent<string>).data) as TrackingView;
            setTracking((current) => !current || update.updatedAt >= current.updatedAt ? update : current);
            setError(false);
          } catch {
            setError(true);
          }
        });
        source.onopen = () => setError(false);
        source.onerror = () => setError(true);
      })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiError && cause.status === 404) setNotFound(true);
        else setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      source?.close();
    };
  }, [trackingNo]);

  return (
    <main className="min-h-dvh bg-background">
      <header className="mx-auto flex w-full max-w-3xl items-center justify-between px-4 py-5">
        <Brand />
        <Link href="/" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> Track another parcel
        </Link>
      </header>
      <section className="mx-auto max-w-3xl px-4 pb-12">
        <div className="rounded-2xl border bg-card p-5 shadow-sm sm:p-8">
          <div className="flex flex-wrap items-start justify-between gap-4 border-b pb-5">
            <div>
              <p className="text-sm text-muted-foreground">Tracking number</p>
              <h1 className="mt-1 break-all font-mono text-2xl font-bold sm:text-3xl">{trackingNo}</h1>
            </div>
            {tracking && <Badge className="uppercase">{formatStatus(tracking.currentStatus)}</Badge>}
          </div>

          {loading && <p className="py-8 text-center text-muted-foreground">Loading tracking details…</p>}
          {!loading && notFound && (
            <div className="py-10 text-center">
              <Package className="mx-auto mb-3 h-8 w-8 text-muted-foreground" />
              <p className="font-semibold">We couldn’t find that parcel.</p>
              <p className="mt-1 text-sm text-muted-foreground">Check the tracking number and try again.</p>
            </div>
          )}
          {!loading && error && !tracking && (
            <p className="py-8 text-center text-destructive">Tracking is temporarily unavailable. Please try again.</p>
          )}

          {tracking && (
            <>
              <div className="my-6 grid gap-3 sm:grid-cols-2">
                <div className="flex items-start gap-3 rounded-lg bg-muted/50 p-4">
                  <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-brand" />
                  <div><p className="text-xs text-muted-foreground">Pickup city</p><p className="font-medium">{tracking.pickupCity ?? "Not available"}</p></div>
                </div>
                <div className="flex items-start gap-3 rounded-lg bg-muted/50 p-4">
                  <Truck className="mt-0.5 h-4 w-4 shrink-0 text-brand" />
                  <div><p className="text-xs text-muted-foreground">Drop-off city</p><p className="font-medium">{tracking.dropoffCity ?? "Not available"}</p></div>
                </div>
              </div>
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-lg font-semibold">Parcel timeline</h2>
                <span className="text-xs text-muted-foreground" aria-live="polite">
                  {error ? "Live updates reconnecting…" : "Live updates enabled"}
                </span>
              </div>
              <ol className="mt-4 space-y-0">
                {tracking.timeline.map((item, index) => (
                  <li key={`${item.status}-${item.occurredAt}`} className="relative flex gap-4 pb-6 last:pb-0">
                    {index < tracking.timeline.length - 1 && <span className="absolute left-[13px] top-7 h-full w-px bg-border" />}
                    <span className="relative z-10 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand/10 text-brand">
                      {item.status === "DELIVERED" ? <CheckCircle2 className="h-4 w-4" /> : <Clock className="h-4 w-4" />}
                    </span>
                    <div className="flex flex-1 flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                      <p className="font-medium">{formatStatus(item.status)}</p>
                      <time className="text-xs text-muted-foreground" dateTime={item.occurredAt}>
                        {new Date(item.occurredAt).toLocaleString("en-LK", { dateStyle: "medium", timeStyle: "short" })}
                      </time>
                    </div>
                  </li>
                ))}
              </ol>
            </>
          )}
        </div>
      </section>
    </main>
  );
}
