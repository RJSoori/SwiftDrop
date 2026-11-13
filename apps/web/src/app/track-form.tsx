"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { api, ApiError, type ParcelDetailResponse, type ParcelStatus } from "@/lib/api";
import { CheckCircle2, Clock, MapPin, Truck, AlertCircle, ArrowRight } from "lucide-react";

function getStatusBadgeVariant(status: ParcelStatus) {
  switch (status) {
    case "DELIVERED":
      return "default";
    case "IN_TRANSIT":
    case "OUT_FOR_DELIVERY":
    case "PICKED_UP":
      return "secondary";
    case "FAILED":
    case "RETURNED":
      return "destructive";
    default:
      return "outline";
  }
}

function formatStatus(status: ParcelStatus): string {
  return status.replace(/_/g, " ");
}

export function TrackForm() {
  const searchParams = useSearchParams();
  const [waybill, setWaybill] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<ParcelDetailResponse | null>(null);

  const fetchTrackDetails = useCallback(async (query: string) => {
    setLoading(true);
    setError(null);
    setDetail(null);

    try {
      const res = await api<ParcelDetailResponse>(`/parcels/track/${encodeURIComponent(query)}`);
      setDetail(res);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setError(`No parcel found for tracking number "${query}". Please check the number and try again.`);
      } else {
        setError("Unable to retrieve tracking information right now. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const trackParam = searchParams.get("track")?.trim();
    if (trackParam) {
      setWaybill(trackParam);
      fetchTrackDetails(trackParam);
    }
  }, [searchParams, fetchTrackDetails]);

  async function handleTrack(e: React.FormEvent) {
    e.preventDefault();
    const query = waybill.trim();
    if (!query) return;
    await fetchTrackDetails(query);
  }

  function handleReset() {
    setDetail(null);
    setError(null);
    setWaybill("");
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

          {!detail ? (
            <form onSubmit={handleTrack}>
              <label htmlFor="waybill" className="mt-5 block text-sm font-medium">
                Waybill tracking number
              </label>
              <input
                id="waybill"
                name="waybill"
                required
                value={waybill}
                autoComplete="off"
                placeholder="e.g. SD-20261107-ABC123"
                onChange={(e) => {
                  setWaybill(e.target.value);
                  setError(null);
                }}
                className="tabular mt-2 w-full border-b-2 border-foreground bg-transparent pb-2 text-2xl font-semibold tracking-wide outline-none placeholder:text-muted-foreground/50 focus-visible:border-brand sm:text-3xl"
              />

              <Button type="submit" size="lg" disabled={loading || !waybill.trim()} className="mt-6 h-11 w-full text-base">
                {loading ? "Searching waybill…" : "Track parcel"}
              </Button>

              {error && (
                <div className="mt-4 flex items-start gap-2 rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>{error}</span>
                </div>
              )}
            </form>
          ) : (
            <div className="mt-4 space-y-4">
              <div className="flex items-center justify-between border-b pb-3">
                <div>
                  <p className="text-xs text-muted-foreground">Waybill No</p>
                  <p className="font-mono text-lg font-bold">{detail.parcel.trackingNo}</p>
                </div>
                <Badge variant={getStatusBadgeVariant(detail.parcel.currentStatus)} className="text-xs font-semibold uppercase">
                  {formatStatus(detail.parcel.currentStatus)}
                </Badge>
              </div>

              {/* Route */}
              <div className="rounded-lg bg-muted/40 p-3 text-xs space-y-2">
                <div className="flex items-start gap-2">
                  <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                  <div>
                    <span className="text-muted-foreground">Pickup: </span>
                    <span className="font-medium text-foreground">{detail.parcel.pickupAddress}</span>
                  </div>
                </div>
                <div className="flex items-start gap-2">
                  <ArrowRight className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                  <div>
                    <span className="text-muted-foreground">Delivery: </span>
                    <span className="font-medium text-foreground">{detail.parcel.dropoffAddress}</span>
                  </div>
                </div>
                <div className="flex justify-between pt-1 border-t text-muted-foreground">
                  <span>Weight: {detail.parcel.weightKg} kg ({detail.parcel.size})</span>
                  <span className="font-semibold text-foreground">LKR {Number(detail.parcel.feeLkr).toFixed(2)}</span>
                </div>
              </div>

              {/* Tracking Timeline */}
              <div className="pt-1">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">Tracking History</p>
                <div className="space-y-3 max-h-48 overflow-y-auto pr-1">
                  {detail.history && detail.history.length > 0 ? (
                    detail.history.map((h, i) => (
                      <div key={h.id || i} className="flex items-start gap-3 text-xs">
                        <div className="mt-0.5 rounded-full p-1 bg-brand/10 text-brand">
                          {h.toStatus === "DELIVERED" ? (
                            <CheckCircle2 className="h-3.5 w-3.5 text-green-600" />
                          ) : h.toStatus === "IN_TRANSIT" ? (
                            <Truck className="h-3.5 w-3.5" />
                          ) : (
                            <Clock className="h-3.5 w-3.5" />
                          )}
                        </div>
                        <div className="flex-1">
                          <p className="font-semibold text-foreground">{formatStatus(h.toStatus)}</p>
                          {h.note && <p className="text-muted-foreground">{h.note}</p>}
                        </div>
                        <span className="text-[10px] text-muted-foreground whitespace-nowrap">
                          {new Date(h.occurredAt).toLocaleDateString("en-GB", {
                            day: "2-digit",
                            month: "short",
                            hour: "2-digit",
                            minute: "2-digit"
                          })}
                        </span>
                      </div>
                    ))
                  ) : (
                    <p className="text-xs text-muted-foreground">Order registered in SwiftDrop system.</p>
                  )}
                </div>
              </div>

              <Button variant="outline" size="sm" onClick={handleReset} className="w-full mt-2">
                Track another parcel
              </Button>
            </div>
          )}
        </div>
      </div>
      <div aria-hidden className="h-2 bg-tape" />
    </div>
  );
}
