import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { api } from "./api";

export const deliverySummarySchema = z.object({
  parcelId: z.uuid(),
  status: z.enum(["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"]),
  completed: z.boolean(),
});

export const todaysDeliveriesSchema = z.array(deliverySummarySchema);

export const deliveryDetailsSchema = z.object({
  parcelId: z.uuid(),
  status: deliverySummarySchema.shape.status,
  recipientName: z.string().min(1),
  dropOffAddress: z.string().min(1),
});

export type DeliverySummary = z.infer<typeof deliverySummarySchema>;
export type DeliveryStatus = DeliverySummary["status"];
export type DeliveryDetails = z.infer<typeof deliveryDetailsSchema>;

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
