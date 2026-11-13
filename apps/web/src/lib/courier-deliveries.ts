import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { api } from "./api";

export const deliverySummarySchema = z.object({
  parcelId: z.uuid(),
  status: z.enum(["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"]),
  completed: z.boolean(),
});

export const todaysDeliveriesSchema = z.array(deliverySummarySchema);

export type DeliverySummary = z.infer<typeof deliverySummarySchema>;
export type DeliveryStatus = DeliverySummary["status"];

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
