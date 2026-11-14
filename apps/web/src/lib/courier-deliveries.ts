import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { api } from "./api";

export const deliveryStatusSchema = z.enum([
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "FAILED",
]);

export const courierUpdateStatusSchema = z.enum(["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"]);

export const deliverySummarySchema = z.object({
  parcelId: z.uuid(),
  status: deliveryStatusSchema,
  completed: z.boolean(),
});

export const todaysDeliveriesSchema = z.array(deliverySummarySchema);

export const deliveryDetailsSchema = z.object({
  parcelId: z.uuid(),
  status: deliverySummarySchema.shape.status,
  recipientName: z.string().min(1),
  dropOffAddress: z.string().min(1),
});

export const statusUpdateSchema = z.object({
  toStatus: courierUpdateStatusSchema,
  note: z.string().trim().max(500, "Keep the note under 500 characters"),
});

export const deliveryOtpSchema = z.object({
  otp: z.string().regex(/^\d{4}$/, "Enter the 4-digit OTP"),
  recipientName: z.string().trim().min(1, "Enter the recipient’s name").max(120, "Keep the name under 120 characters"),
});

export const proofImageSchema = z
  .file()
  .max(5 * 1024 * 1024, "Photo must be 5 MB or smaller")
  .refine((file) => file.type === "image/jpeg" || file.type === "image/png", "Choose a JPEG or PNG photo");

export const proofUploadResponseSchema = z.object({
  proofId: z.uuid(),
});

export type DeliverySummary = z.infer<typeof deliverySummarySchema>;
export type DeliveryStatus = DeliverySummary["status"];
export type DeliveryDetails = z.infer<typeof deliveryDetailsSchema>;
export type CourierUpdateStatus = z.infer<typeof courierUpdateStatusSchema>;
export type StatusUpdateInput = z.infer<typeof statusUpdateSchema>;
export type DeliveryOtpInput = z.infer<typeof deliveryOtpSchema>;

const NEXT_COURIER_STATUSES: Record<DeliveryStatus, CourierUpdateStatus[]> = {
  CREATED: ["PICKED_UP"],
  PICKED_UP: ["IN_TRANSIT"],
  IN_TRANSIT: ["OUT_FOR_DELIVERY"],
  OUT_FOR_DELIVERY: [],
  DELIVERED: [],
  FAILED: ["OUT_FOR_DELIVERY"],
};

export function nextCourierStatuses(status: DeliveryStatus): CourierUpdateStatus[] {
  return NEXT_COURIER_STATUSES[status];
}

async function fetchTodaysDeliveries(): Promise<DeliverySummary[]> {
  const response = await api<unknown>("/courier/deliveries/today");
  return todaysDeliveriesSchema.parse(response);
}

export function useTodaysDeliveries() {
  return useQuery({
    queryKey: ["courier", "deliveries", "today"],
    queryFn: fetchTodaysDeliveries,
    refetchInterval: 10_000,
  });
}

async function fetchDeliveryDetails(parcelId: string): Promise<DeliveryDetails> {
  const response = await api<unknown>(`/courier/deliveries/${encodeURIComponent(parcelId)}`);
  return deliveryDetailsSchema.parse(response);
}

export function useDeliveryDetails(parcelId: string) {
  return useQuery({
    queryKey: ["courier", "deliveries", parcelId],
    queryFn: () => fetchDeliveryDetails(parcelId),
    enabled: parcelId.length > 0,
  });
}
