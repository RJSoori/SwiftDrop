"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CircleCheck, CircleX } from "lucide-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { FormError, FormField } from "@/components/form-field";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { ApiError, api } from "@/lib/api";
import {
  failDeliverySchema,
  type FailDeliveryInput,
  type FailureReason,
} from "@/lib/courier-deliveries";

const FAILURE_REASONS: { value: FailureReason; label: string }[] = [
  { value: "RECIPIENT_UNAVAILABLE", label: "Recipient unavailable" },
  { value: "WRONG_ADDRESS", label: "Wrong address" },
  { value: "REFUSED", label: "Recipient refused" },
  { value: "OTHER", label: "Other" },
];

export default function CourierDeliveryFailedPage() {
  const { parcelId } = useParams<{ parcelId: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [markedFailed, setMarkedFailed] = useState(false);
  const form = useForm<FailDeliveryInput>({
    resolver: zodResolver(failDeliverySchema),
    defaultValues: { reasonCode: "RECIPIENT_UNAVAILABLE", note: "" },
  });
  const { errors, isSubmitting } = form.formState;
  const reasonCode = form.watch("reasonCode");

  useEffect(() => {
    void form.trigger("note");
  }, [form, reasonCode]);

  const submit = form.handleSubmit(async (values) => {
    form.clearErrors("root");
    try {
      await api(`/courier/deliveries/${encodeURIComponent(parcelId)}/fail`, {
        method: "POST",
        json: { reasonCode: values.reasonCode, note: values.note },
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) {
        form.setError("root", { message: "This parcel isn’t assigned to you. You can only mark your assigned parcels as failed." });
      } else if (error instanceof ApiError && error.status === 400) {
        const hasReasonError = Boolean(error.fieldErrors.reasonCode);
        const hasNoteError = Object.entries(error.fieldErrors).some(
          ([field, message]) => field.toLowerCase().includes("note") || message.toLowerCase().includes("note"),
        );
        if (hasReasonError) {
          form.setError("reasonCode", { message: "Choose a reason for the failed delivery." });
        }
        if (hasNoteError) {
          form.setError("note", { message: "Add a note when the reason is Other." });
        }
        if (!hasReasonError && !hasNoteError) {
          form.setError("root", {
            message: "Check the failure reason and note. A note is required when you choose Other.",
          });
        }
      } else if (error instanceof ApiError) {
        form.setError("root", { message: error.message });
      } else {
        form.setError("root", { message: "Could not mark this delivery as failed. Check your connection and try again." });
      }
      return;
    }

    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["courier", "deliveries", parcelId] }),
      queryClient.invalidateQueries({ queryKey: ["courier", "deliveries", "today"] }),
    ]);
    setMarkedFailed(true);
  });

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

      <PageHeading title="Delivery failed" intro="Record why this parcel could not be delivered." />

      <Card className="mt-8 max-w-xl border-2 shadow-sm">
        <CardContent>
          {markedFailed ? (
            <div role="status" className="grid gap-3">
              <p className="flex items-center gap-2 font-semibold text-emerald-800 dark:text-emerald-200">
                <CircleCheck aria-hidden className="size-5" />
                Marked as failed
              </p>
              <p className="text-sm text-muted-foreground">Parcel {parcelId} has been marked as failed.</p>
              <Button
                type="button"
                variant="outline"
                onClick={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}`)}
                className="min-h-11 w-full"
              >
                Back to parcel details
              </Button>
            </div>
          ) : (
            <form onSubmit={submit} noValidate className="grid gap-5">
              <div>
                <p className="text-sm text-muted-foreground">Parcel</p>
                <p className="mt-1 break-all font-medium tabular">{parcelId}</p>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="reasonCode">Reason</Label>
                <select
                  id="reasonCode"
                  className="h-10 w-full rounded-lg border border-input bg-transparent px-2.5 py-1 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
                  aria-invalid={!!errors.reasonCode}
                  aria-describedby={errors.reasonCode ? "reasonCode-error" : undefined}
                  disabled={isSubmitting}
                  {...form.register("reasonCode")}
                >
                  {FAILURE_REASONS.map((reason) => (
                    <option key={reason.value} value={reason.value}>
                      {reason.label}
                    </option>
                  ))}
                </select>
                {errors.reasonCode?.message && (
                  <p id="reasonCode-error" className="text-sm text-destructive">
                    {errors.reasonCode.message}
                  </p>
                )}
              </div>

              <FormField
                id="note"
                label={reasonCode === "OTHER" ? "Note (required)" : "Note (optional)"}
                placeholder={reasonCode === "OTHER" ? "Explain why delivery could not be completed" : "Add a note"}
                maxLength={500}
                hint={reasonCode === "OTHER" ? undefined : "A note is required when you choose Other."}
                error={errors.note?.message}
                disabled={isSubmitting}
                {...form.register("note")}
              />

              <FormError message={errors.root?.message} />

              <div className="grid gap-3 sm:grid-cols-2">
                <Button type="submit" disabled={isSubmitting} className="min-h-11 w-full">
                  <CircleX aria-hidden />
                  {isSubmitting ? "Saving…" : "Mark as failed"}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => router.push(`/courier/deliveries/${encodeURIComponent(parcelId)}`)}
                  disabled={isSubmitting}
                  className="min-h-11 w-full"
                >
                  Cancel
                </Button>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
