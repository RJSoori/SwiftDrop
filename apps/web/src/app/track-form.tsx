"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";

export function TrackForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [trackingNo, setTrackingNo] = useState("");

  useEffect(() => {
    const trackingParam = searchParams.get("track")?.trim();
    if (trackingParam) {
      router.replace(`/track/${encodeURIComponent(trackingParam)}`);
    }
  }, [router, searchParams]);

  function handleTrack(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = trackingNo.trim();
    if (query) router.push(`/track/${encodeURIComponent(query)}`);
  }

  return (
    <div className="relative overflow-hidden rounded-xl border-2 border-foreground bg-card shadow-[6px_6px_0_0_var(--foreground)]">
      <div className="flex items-stretch">
        <div aria-hidden className="barcode w-14 shrink-0 text-foreground sm:w-20" />
        <div className="flex-1 p-5 sm:p-7">
          <div className="flex items-baseline justify-between gap-4 border-b-2 border-dashed border-border pb-3">
            <span className="display text-xl">Waybill</span>
            <span className="text-sm text-muted-foreground">SwiftDrop Express</span>
          </div>
          <form onSubmit={handleTrack}>
            <label htmlFor="waybill" className="mt-5 block text-sm font-medium">
              Waybill tracking number
            </label>
            <input
              id="waybill"
              name="waybill"
              required
              value={trackingNo}
              autoComplete="off"
              placeholder="e.g. SD-20261107-ABC123"
              onChange={(event) => setTrackingNo(event.target.value)}
              className="tabular mt-2 w-full border-b-2 border-foreground bg-transparent pb-2 text-2xl font-semibold tracking-wide outline-none placeholder:text-muted-foreground/50 focus-visible:border-brand sm:text-3xl"
            />
            <Button type="submit" size="lg" disabled={!trackingNo.trim()} className="mt-6 h-11 w-full text-base">
              Track parcel <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
