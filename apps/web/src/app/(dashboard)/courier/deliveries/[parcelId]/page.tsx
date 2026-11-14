"use client";

import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Camera, CircleCheck, CircleX, PencilLine, Phone, UserRound } from "lucide-react";
import { FormError } from "@/components/form-field";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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
    <div className="max-w-3xl">
      <Button variant="outline" onClick={() => router.push("/courier")} className="mb-6 min-h-10">
        <ArrowLeft aria-hidden />
        Today&apos;s deliveries
      </Button>

      <PageHeading title="Parcel details" intro="Review the delivery details and choose your next action." />

      {delivery.isPending ? (
        <p role="status" className="mt-8 text-muted-foreground">
          Loading parcel details…
        </p>
      ) : delivery.isError ? (
        <div className="mt-8 grid justify-items-start gap-3">
          {delivery.error instanceof ApiError && delivery.error.status === 403 ? (
            <FormError message="This parcel isn’t assigned to you. Only its assigned courier can view these details." />
          ) : (
            <>
              <FormError
                message={
                  delivery.error instanceof ApiError
                    ? `Could not load parcel details. ${delivery.error.message}`
                    : "Could not load parcel details. Check your connection and try again."
                }
              />
              <Button onClick={() => void delivery.refetch()} disabled={delivery.isFetching}>
                Try again
              </Button>
            </>
          )}
        </div>
      ) : (
        <>
          <Card aria-label="Parcel information" className="mt-8 max-w-3xl">
            <CardContent className="grid gap-6">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Parcel</p>
                  <p className="mt-1 break-all font-semibold tabular">{delivery.data.parcelId}</p>
                </div>
                <span className="w-fit shrink-0 rounded-full bg-accent px-3 py-1 text-sm font-medium text-accent-foreground">
                  {STATUS_LABELS[delivery.data.status]}
                </span>
              </div>

              <dl className="grid gap-5 border-t pt-5">
                <div className="flex min-w-0 gap-3">
                  <UserRound aria-hidden className="mt-0.5 size-5 shrink-0 text-muted-foreground" />
                  <div className="min-w-0">
                    <dt className="text-sm text-muted-foreground">Recipient</dt>
                    <dd className="mt-1 break-words font-medium">{delivery.data.recipientName}</dd>
                  </div>
                </div>
                <div className="flex min-w-0 gap-3">
                  <Phone aria-hidden className="mt-0.5 size-5 shrink-0 text-muted-foreground" />
                  <div className="min-w-0">
                    <dt className="text-sm text-muted-foreground">Recipient phone</dt>
                    <dd className="mt-1 break-words font-medium">{delivery.data.recipientPhone}</dd>
                  </div>
                </div>
              </dl>
            </CardContent>
          </Card>

          <section aria-labelledby="parcel-actions-heading" className="mt-8">
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
