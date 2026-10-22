"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";

/** The waybill strip. Tracking itself arrives with parcel-service in Phase 2. */
export function TrackForm() {
  const [submitted, setSubmitted] = useState(false);

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        setSubmitted(true);
      }}
      className="relative overflow-hidden rounded-xl border-2 border-foreground bg-card shadow-[6px_6px_0_0_var(--foreground)]"
    >
      <div className="flex items-stretch">
        <div aria-hidden className="barcode w-14 shrink-0 text-foreground sm:w-20" />
        <div className="flex-1 p-5 sm:p-7">
          <div className="flex items-baseline justify-between gap-4 border-b-2 border-dashed border-border pb-3">
            <span className="display text-xl">Waybill</span>
            <span className="text-sm text-muted-foreground">SwiftDrop Express</span>
          </div>

          <label htmlFor="waybill" className="mt-5 block text-sm font-medium">
            Waybill number
          </label>
          <input
            id="waybill"
            name="waybill"
            required
            autoComplete="off"
            placeholder="SD-2026-004817"
            onChange={() => setSubmitted(false)}
            className="tabular mt-2 w-full border-b-2 border-foreground bg-transparent pb-2 text-2xl font-semibold tracking-wide outline-none placeholder:text-muted-foreground/60 focus-visible:border-brand sm:text-3xl"
          />

          <Button type="submit" size="lg" className="mt-6 h-11 w-full text-base">
            Track parcel
          </Button>
          <p aria-live="polite" className="mt-3 min-h-5 text-sm text-muted-foreground">
            {submitted && "Parcel tracking opens in the next release."}
          </p>
        </div>
      </div>
      <div aria-hidden className="h-2 bg-tape" />
    </form>
  );
}
