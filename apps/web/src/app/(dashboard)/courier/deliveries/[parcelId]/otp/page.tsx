"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CircleCheck } from "lucide-react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { FormError, FormField } from "@/components/form-field";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ApiError, api } from "@/lib/api";
import { deliveryOtpSchema, type DeliveryOtpInput } from "@/lib/courier-deliveries";

export default function CourierDeliveryOtpPage() {
  return (
    <Suspense fallback={<p role="status" className="text-muted-foreground">Loading delivery confirmation…</p>}>
      <CourierDeliveryOtpForm />
    </Suspense>
  );
}

function CourierDeliveryOtpForm() {
  const { parcelId } = useParams<{ parcelId: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const proofId = searchParams.get("proofId");
  const queryClient = useQueryClient();
  const [delivered, setDelivered] = useState(false);
  const [otpLocked, setOtpLocked] = useState(false);
  const form = useForm<DeliveryOtpInput>({
    resolver: zodResolver(deliveryOtpSchema),
    defaultValues: { otp: "", recipientName: "" },
  });
  const { errors, isSubmitting } = form.formState;

  useEffect(() => {
    if (!proofId) {
      router.replace(`/courier/deliveries/${encodeURIComponent(parcelId)}/proof`);
    }
  }, [parcelId, proofId, router]);

  const submit = form.handleSubmit(async (values) => {
    form.clearErrors("root");
    try {
      await api(`/courier/deliveries/${encodeURIComponent(parcelId)}/deliver`, {
        method: "POST",
        json: { ...values, proofId },
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) {
        form.setError("root", { message: "This parcel isn’t assigned to you. You can only confirm delivery for your assigned parcels." });
      } else if (error instanceof ApiError && error.status === 400 && error.message.toLowerCase().includes("incorrect otp")) {
        form.setError("root", {
          message: "That OTP is incorrect. You have a maximum of 5 attempts; after 5 incorrect entries, the OTP will be locked.",
        });
      } else if (error instanceof ApiError && error.status === 400 && error.message.toLowerCase().includes("locked")) {
        setOtpLocked(true);
        form.setError("root", { message: "This OTP is locked after 5 incorrect attempts. Ask the dispatcher for help." });
      } else if (error instanceof ApiError && error.status === 400 && error.message.toLowerCase().includes("expired")) {
        form.setError("root", { message: "This OTP has expired. Ask the dispatcher for a new OTP." });
      } else if (error instanceof ApiError && error.status === 400 && error.message.toLowerCase().includes("proof image is required")) {
        form.setError("root", { message: "Photo proof is missing. Return to Photo proof and upload a photo before confirming delivery." });
      } else if (error instanceof ApiError) {
        form.setError("root", { message: error.message });
      } else {
        form.setError("root", { message: "Could not confirm delivery. Check your connection and try again." });
      }
      return;
    }

    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["courier", "deliveries", parcelId] }),
      queryClient.invalidateQueries({ queryKey: ["courier", "deliveries", "today"] }),
    ]);
    setDelivered(true);
  });

  if (!proofId) {
    return <p role="status" className="text-muted-foreground">Returning to Photo proof…</p>;
  }

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

      <PageHeading title="Enter delivery OTP" intro="Confirm the recipient’s identity to complete this delivery." />

      <Card className="mt-8 max-w-xl">
        <CardContent>
          {delivered ? (
            <div role="status" className="grid gap-3">
              <p className="flex items-center gap-2 font-semibold text-emerald-200">
                <CircleCheck aria-hidden className="size-5" />
                Delivered
              </p>
              <p className="text-sm text-muted-foreground">
                Parcel {parcelId} has been marked as delivered.
              </p>
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

              <FormField
                id="otp"
                label="4-digit OTP"
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                autoComplete="one-time-code"
                maxLength={4}
                placeholder="1234"
                hint="Enter digits only. You have a maximum of 5 attempts."
                error={errors.otp?.message}
                disabled={otpLocked || isSubmitting}
                {...form.register("otp")}
              />
              <FormField
                id="recipientName"
                label="Recipient name"
                autoComplete="name"
                placeholder="Name of the person receiving the parcel"
                error={errors.recipientName?.message}
                disabled={otpLocked || isSubmitting}
                {...form.register("recipientName")}
              />

              <FormError message={errors.root?.message} />

              <Button type="submit" disabled={otpLocked || isSubmitting} className="min-h-11 w-full">
                {isSubmitting ? "Confirming delivery…" : "Confirm delivery"}
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
