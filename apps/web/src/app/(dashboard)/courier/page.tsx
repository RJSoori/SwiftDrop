"use client";

import { RefreshCw } from "lucide-react";
import { useState } from "react";
import Link from "next/link";
import { FormField } from "@/components/form-field";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { useTodaysDeliveries, type DeliverySummary, type DeliveryStatus } from "@/lib/courier-deliveries";

type DeliveryTab = "active" | "completed";

const STATUS_LABELS: Record<DeliveryStatus, string> = {
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  FAILED: "Failed",
};

export default function CourierDashboard() {
  const deliveries = useTodaysDeliveries();
  const [tab, setTab] = useState<DeliveryTab>("active");
  const [search, setSearch] = useState("");

  const active = deliveries.data?.filter((delivery) => !delivery.completed) ?? [];
  const completed = deliveries.data?.filter((delivery) => delivery.completed) ?? [];
  const visible = (tab === "active" ? active : completed).filter((delivery) =>
    delivery.parcelId.toLowerCase().includes(search.trim().toLowerCase()),
  );
  const requestError = getRequestError(deliveries.error);

  return (
    <div className="mx-auto max-w-4xl">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <PageHeading title="Today's deliveries" intro="Stay on top of every parcel on your route." />
        <Button
          variant="outline"
          size="default"
          onClick={() => void deliveries.refetch()}
          disabled={deliveries.isFetching}
          aria-label="Refresh deliveries"
          className="self-start sm:self-auto"
        >
          <RefreshCw aria-hidden className={deliveries.isFetching ? "animate-spin" : undefined} />
          Refresh
        </Button>
      </div>

      <div className="mt-7 grid gap-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
        <FormField
          id="delivery-search"
          label="Search by parcel ID"
          type="search"
          placeholder="Enter a parcel ID"
          value={search}
          onChange={(event) => setSearch(event.currentTarget.value)}
        />
        <div role="tablist" aria-label="Delivery status" className="flex gap-2">
          <DeliveryTabButton
            label="Active"
            count={active.length}
            selected={tab === "active"}
            onClick={() => setTab("active")}
          />
          <DeliveryTabButton
            label="Completed"
            count={completed.length}
            selected={tab === "completed"}
            onClick={() => setTab("completed")}
          />
        </div>
      </div>

      {deliveries.isPending ? (
        <section aria-label="Loading deliveries" className="mt-6 grid gap-3">
          {[0, 1, 2].map((item) => (
            <div key={item} className="h-24 animate-pulse rounded-xl border bg-card" />
          ))}
        </section>
      ) : deliveries.isError && !deliveries.data ? (
        <div role="alert" className="mt-6 rounded-xl border border-destructive/30 bg-destructive/10 p-4">
          <p className="font-medium text-destructive">Unable to load your deliveries.</p>
          <p className="mt-1 text-sm text-muted-foreground">{requestError}</p>
          <Button className="mt-4" onClick={() => void deliveries.refetch()} disabled={deliveries.isFetching}>
            Try again
          </Button>
        </div>
      ) : (
        <>
          {deliveries.isError && (
            <p role="status" className="mt-5 rounded-lg border border-border bg-muted px-4 py-3 text-sm text-muted-foreground">
              Could not refresh the list. Showing the last loaded deliveries.
            </p>
          )}
          {visible.length > 0 ? (
            <ul className="mt-5 grid gap-3">
              {visible.map((delivery) => (
                <li key={delivery.parcelId}>
                  <DeliveryCard delivery={delivery} />
                </li>
              ))}
            </ul>
          ) : (
            <div className="mt-5 rounded-xl border border-dashed bg-card px-4 py-10 text-center">
              <h2 className="font-semibold">
                {search.trim()
                  ? "No matching deliveries"
                  : tab === "active"
                    ? "No active deliveries"
                    : "No completed deliveries yet"}
              </h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {search.trim()
                  ? "Try a different parcel ID."
                  : tab === "active"
                    ? "New assignments will show up here automatically."
                    : "Your finished deliveries will appear here."}
              </p>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function DeliveryTabButton({
  label,
  count,
  selected,
  onClick,
}: {
  label: string;
  count: number;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={selected}
      onClick={onClick}
      className={
        "inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border px-3 text-sm font-medium transition-colors " +
        (selected
          ? "border-primary bg-primary text-primary-foreground"
          : "bg-card text-muted-foreground hover:bg-muted hover:text-foreground")
      }
    >
      {label}
      <span className={selected ? "tabular rounded-full bg-primary-foreground/15 px-1.5 py-0.5 text-xs" : "tabular rounded-full bg-muted px-1.5 py-0.5 text-xs"}>
        {count}
      </span>
    </button>
  );
}

function DeliveryCard({ delivery }: { delivery: DeliverySummary }) {
  const statusColor =
    delivery.status === "FAILED"
      ? "bg-destructive/10 text-destructive"
      : delivery.status === "DELIVERED"
        ? "bg-emerald-600/10 text-emerald-700 dark:text-emerald-300"
        : "bg-accent text-accent-foreground";

  return (
    <Link
      href={`/courier/deliveries/${encodeURIComponent(delivery.parcelId)}`}
      className="block rounded-xl focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
      aria-label={`View parcel ${delivery.parcelId}, ${STATUS_LABELS[delivery.status]}`}
    >
      <article className="flex min-w-0 flex-col gap-3 rounded-xl border bg-card p-4 transition-colors hover:bg-muted/50 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Parcel</p>
          <p className="mt-1 break-all font-semibold tabular">{delivery.parcelId}</p>
        </div>
        <span className={`w-fit shrink-0 rounded-full px-3 py-1 text-sm font-medium ${statusColor}`}>
          {STATUS_LABELS[delivery.status]}
        </span>
      </article>
    </Link>
  );
}

function getRequestError(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "The delivery list could not be read. Check your connection and try again.";
}
