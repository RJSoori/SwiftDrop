"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, Save } from "lucide-react";
import { useParams, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { FormError, FormField, applyServerErrors } from "@/components/form-field";
import { PageHeading } from "@/components/dashboard";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { ApiError, api } from "@/lib/api";
import {
  nextCourierStatuses,
  statusUpdateSchema,
  useDeliveryDetails,
  type CourierUpdateStatus,
  type DeliveryStatus,
  type StatusUpdateInput,
} from "@/lib/courier-deliveries";

const STATUS_LABELS: Record<CourierUpdateStatus, string> = {
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
};

export default function UpdateCourierStatusPage() {
  const { parcelId } = useParams<{ parcelId: string }>();
  const router = useRouter();
  const delivery = useDeliveryDetails(parcelId);
  const nextStatuses = delivery.data ? nextCourierStatuses(delivery.data.status) : [];

  return (
    <div className="max-w-2xl">
      <Button
        variant="outline"
        onClick={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}`)}
        className="mb-6 min-h-10"
      >
        <ArrowLeft aria-hidden />
        Parcel details
      </Button>

      <PageHeading title="Update status" intro="Choose the next valid step for this parcel." />

      {delivery.isPending ? (
        <p role="status" className="mt-8 text-muted-foreground">
          Loading parcel details…
        </p>
      ) : delivery.isError ? (
        <div className="mt-8 grid justify-items-start gap-3">
          {delivery.error instanceof ApiError && delivery.error.status === 403 ? (
            <FormError message="This parcel isn’t assigned to you. Only its assigned courier can change its status." />
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
      ) : nextStatuses.length === 0 ? (
        <section className="mt-8 max-w-xl rounded-xl border border-dashed p-8">
          <p className="font-medium">No status updates are available for this parcel.</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Current status: {delivery.data.status.replaceAll("_", " ")}.
          </p>
        </section>
      ) : (
        <StatusUpdateForm
          parcelId={parcelId}
          currentStatus={delivery.data.status}
          options={nextStatuses}
          onBack={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}`)}
          onUpdated={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}`)}
          refreshParcel={async () => {
            const result = await delivery.refetch();
            return !result.isError;
          }}
        />
      )}
    </div>
  );
}

function StatusUpdateForm({
  parcelId,
  currentStatus,
  options,
  onBack,
  onUpdated,
  refreshParcel,
}: {
  parcelId: string;
  currentStatus: DeliveryStatus;
  options: CourierUpdateStatus[];
  onBack: () => void;
  onUpdated: () => void;
  refreshParcel: () => Promise<boolean>;
}) {
  const queryClient = useQueryClient();
  const form = useForm<StatusUpdateInput>({
    resolver: zodResolver(statusUpdateSchema),
    defaultValues: { toStatus: options[0]!, note: "" },
  });

  useEffect(() => {
    if (options.length > 0 && !options.includes(form.getValues("toStatus"))) {
      form.setValue("toStatus", options[0]!, { shouldValidate: true });
    }
  }, [form, options]);

  const submit = form.handleSubmit(async (values) => {
    form.clearErrors("root");
    try {
      await api(`/courier/deliveries/${encodeURIComponent(parcelId)}/status`, {
        method: "POST",
        json: { toStatus: values.toStatus, note: values.note || undefined },
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        form.setError("root", {
          message: "This status change is no longer allowed. The parcel status has been refreshed.",
        });
        await refreshParcel();
        return;
      }
      if (error instanceof ApiError && error.status === 403) {
        form.setError("root", { message: "You can’t update this parcel because it isn’t assigned to you." });
        return;
      }
      applyServerErrors(form, error);
      return;
    }

    await queryClient.invalidateQueries({ queryKey: ["courier", "deliveries", "today"] });
    if (!(await refreshParcel())) {
      form.setError("root", { message: "Status updated, but parcel details could not be refreshed. Try again." });
      return;
    }
    onUpdated();
  });

  return (
    <Card className="mt-8 max-w-xl border-2 shadow-sm">
      <CardContent>
        <form onSubmit={submit} className="grid gap-5">
          <div className="grid gap-1">
            <p className="text-sm text-muted-foreground">Current status</p>
            <p className="font-medium">{currentStatus.replaceAll("_", " ")}</p>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="toStatus">Next status</Label>
            <select
              id="toStatus"
              className="h-10 w-full rounded-lg border border-input bg-transparent px-2.5 py-1 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
              aria-invalid={!!form.formState.errors.toStatus}
              aria-describedby={form.formState.errors.toStatus ? "toStatus-error" : undefined}
              {...form.register("toStatus")}
            >
              {options.map((status) => (
                <option key={status} value={status}>
                  {STATUS_LABELS[status]}
                </option>
              ))}
            </select>
            {form.formState.errors.toStatus?.message && (
              <p id="toStatus-error" className="text-sm text-destructive">
                {form.formState.errors.toStatus.message}
              </p>
            )}
          </div>

          <FormField
            id="note"
            label="Note (optional)"
            placeholder="Add a note for this status update"
            maxLength={500}
            error={form.formState.errors.note?.message}
            {...form.register("note")}
          />

          <FormError message={form.formState.errors.root?.message} />

          <div className="grid gap-3 sm:grid-cols-2">
            <Button
              type="submit"
              disabled={form.formState.isSubmitting}
              className="min-h-11 w-full"
            >
              <Save aria-hidden />
              {form.formState.isSubmitting ? "Saving…" : "Save status"}
            </Button>
            <Button type="button" variant="outline" onClick={onBack} className="min-h-11 w-full">
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
