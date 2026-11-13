"use client";

import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Camera, CircleCheck, CircleX, MapPin, PencilLine, UserRound } from "lucide-react";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { useDeliveryDetails, type DeliveryStatus } from "@/lib/courier-deliveries";

const STATUS_LABELS: Record<DeliveryStatus, string> = {
  CREATED: "Created",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  FAILED: "Failed",
};

export default function CourierParcelDetailPage() {
  const { parcelId } = useParams<{ parcelId: string }>();
  const router = useRouter();
  const delivery = useDeliveryDetails(parcelId);

  return (
    <div className="mx-auto max-w-3xl">
      <Button variant="outline" onClick={() => router.push("/courier")} className="mb-6 min-h-10">
        <ArrowLeft aria-hidden />
        Today&apos;s deliveries
      </Button>

      <PageHeading title="Parcel details" intro="Review the delivery details and choose your next action." />

      {delivery.isPending ? (
        <section aria-label="Loading parcel details" className="mt-6 grid gap-3">
          <div className="h-28 animate-pulse rounded-xl border bg-card" />
          <div className="h-40 animate-pulse rounded-xl border bg-card" />
        </section>
      ) : delivery.isError ? (
        <div role="alert" className="mt-6 rounded-xl border border-destructive/30 bg-destructive/10 p-4">
          {delivery.error instanceof ApiError && delivery.error.status === 403 ? (
            <>
              <h2 className="font-semibold text-destructive">You don&apos;t have access to this delivery.</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Only the assigned courier can view these parcel details.
              </p>
            </>
          ) : (
            <>
              <h2 className="font-semibold text-destructive">Unable to load parcel details.</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {delivery.error instanceof ApiError
                  ? delivery.error.message
                  : "The parcel details could not be read. Check your connection and try again."}
              </p>
              <Button className="mt-4" onClick={() => void delivery.refetch()} disabled={delivery.isFetching}>
                Try again
              </Button>
            </>
          )}
        </div>
      ) : (
        <>
          <section aria-label="Parcel information" className="mt-6 rounded-xl border bg-card p-4 sm:p-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Parcel</p>
                <p className="mt-1 break-all font-semibold tabular">{delivery.data.parcelId}</p>
              </div>
              <span className="w-fit shrink-0 rounded-full bg-accent px-3 py-1 text-sm font-medium text-accent-foreground">
                {STATUS_LABELS[delivery.data.status]}
              </span>
            </div>

            <dl className="mt-6 grid gap-5 border-t pt-5">
              <div className="flex min-w-0 gap-3">
                <UserRound aria-hidden className="mt-0.5 size-5 shrink-0 text-muted-foreground" />
                <div className="min-w-0">
                  <dt className="text-sm text-muted-foreground">Recipient</dt>
                  <dd className="mt-1 break-words font-medium">{delivery.data.recipientName}</dd>
                </div>
              </div>
              <div className="flex min-w-0 gap-3">
                <MapPin aria-hidden className="mt-0.5 size-5 shrink-0 text-muted-foreground" />
                <div className="min-w-0">
                  <dt className="text-sm text-muted-foreground">Drop-off address</dt>
                  <dd className="mt-1 break-words font-medium">{delivery.data.dropOffAddress}</dd>
                </div>
              </div>
            </dl>
          </section>

          <section aria-labelledby="parcel-actions-heading" className="mt-7">
            <h2 id="parcel-actions-heading" className="text-base font-semibold">Delivery actions</h2>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <ActionButton
                label="Update status"
                icon={<PencilLine aria-hidden />}
                onClick={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}/status`)}
              />
              <ActionButton
                label="Photo proof"
                icon={<Camera aria-hidden />}
                onClick={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}/proof`)}
              />
              <ActionButton
                label="Enter delivery OTP"
                icon={<CircleCheck aria-hidden />}
                onClick={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}/otp`)}
              />
              <ActionButton
                label="Delivery failed"
                icon={<CircleX aria-hidden />}
                onClick={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}/fail`)}
              />
            </div>
          </section>
        </>
      )}
    </div>
  );
}

function ActionButton({
  label,
  icon,
  onClick,
}: {
  label: string;
  icon: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <Button variant="outline" onClick={onClick} className="min-h-11 w-full justify-start gap-3 px-4">
      {icon}
      {label}
    </Button>
  );
}
