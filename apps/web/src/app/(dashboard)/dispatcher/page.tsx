"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { api, ApiError } from "@/lib/api";

type Parcel = {
  parcelId: string;
  trackingNo: string;
  pickupCity: string;
  dropoffCity: string;
  status: string;
  courierId?: string;
};
type Page<T> = { content: T[]; number: number; totalPages: number; totalElements: number };
type Courier = {
  userId: string;
  fullName: string;
  vehicleType: string;
  activeParcelCount: number;
};

export default function DispatcherDashboard() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");
  const [courierId, setCourierId] = useState("");
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState<string>();
  const parcels = useQuery({
    queryKey: ["dispatch", "parcels", status, courierId, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: "10" });
      if (status && status !== "ALL") params.set("status", status);
      if (courierId) params.set("courierId", courierId);
      return api<Page<Parcel>>(`/dispatch/parcels${status || courierId ? `?${params}` : `/unassigned?${params}`}`);
    },
    refetchInterval: 10_000,
  });
  const couriers = useQuery({
    queryKey: ["dispatch", "workloads"],
    queryFn: () => api<Courier[]>("/dispatch/couriers/workloads"),
    refetchInterval: 10_000,
  });
  const assign = useMutation({
    mutationFn: ({ parcelId, courierId }: { parcelId: string; courierId: string }) =>
      api(`/dispatch/parcels/${parcelId}/assign`, { method: "POST", json: { courierId } }),
    onSuccess: async () => {
      setMessage("Parcel assigned.");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["dispatch", "parcels"] }),
        queryClient.invalidateQueries({ queryKey: ["dispatch", "workloads"] }),
      ]);
    },
    onError: (error) => {
      setMessage(error instanceof ApiError && error.status === 409
        ? "That parcel was assigned by someone else. The list was refreshed."
        : "Unable to assign the parcel.");
      void queryClient.invalidateQueries({ queryKey: ["dispatch", "parcels"] });
    },
  });

  return (
    <>
      <PageHeading title="Dispatcher desk" intro="Review the queue, compare courier workloads, and assign deliveries." />
      {message ? <p className="mt-4 text-sm text-muted-foreground" role="status">{message}</p> : null}
      <div className="mt-8 grid gap-6 xl:grid-cols-[1.4fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>{status ? "All parcels" : "Unassigned queue"}</CardTitle>
            <CardDescription>Filter the local dispatch queue without calling parcel-service.</CardDescription>
            <select aria-label="Filter parcels by status" value={status} onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
              className="mt-3 h-9 w-48 rounded-lg border bg-background px-3 text-sm">
              <option value="">Unassigned queue</option>
              <option value="ALL">All statuses</option>
              <option value="CREATED">Unassigned / created</option>
              <option value="PICKED_UP">Picked up</option>
              <option value="IN_TRANSIT">In transit</option>
              <option value="OUT_FOR_DELIVERY">Out for delivery</option>
              <option value="DELIVERED">Delivered</option>
              <option value="FAILED">Failed</option>
            </select>
            <select aria-label="Filter parcels by courier" value={courierId} onChange={(event) => {
              setCourierId(event.target.value);
              setPage(0);
            }} className="ml-2 mt-3 h-9 w-48 rounded-lg border bg-background px-3 text-sm">
              <option value="">All couriers</option>
              {couriers.data?.map((courier) => <option key={courier.userId} value={courier.userId}>{courier.fullName}</option>)}
            </select>
          </CardHeader>
          <CardContent className="space-y-3">
            {parcels.isPending ? <p className="text-muted-foreground">Loading queue…</p> : null}
            {parcels.data?.content.length === 0 ? <p className="text-muted-foreground">No parcels match this filter.</p> : null}
            {parcels.data?.content.map((parcel) => (
              <ParcelRow key={parcel.parcelId} parcel={parcel} couriers={couriers.data ?? []}
                disabled={assign.isPending} onAssign={(courierId) => assign.mutate({ parcelId: parcel.parcelId, courierId })} />
            ))}
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>Courier workloads</CardTitle><CardDescription>Active assigned parcels per courier.</CardDescription></CardHeader>
          <CardContent className="space-y-3">
            {couriers.data?.map((courier) => (
              <button type="button" key={courier.userId} onClick={() => {
                setCourierId(courier.userId);
                setStatus("ALL");
                setPage(0);
              }} className="flex w-full items-center justify-between border-b pb-3 text-left last:border-0">
                <div><p className="font-medium">{courier.fullName}</p><p className="text-sm text-muted-foreground">{courier.vehicleType}</p></div>
                <span className="rounded-full bg-muted px-3 py-1 text-sm">{courier.activeParcelCount} active</span>
              </button>
            ))}
          </CardContent>
        </Card>
      </div>
      {parcels.data && parcels.data.totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-end gap-3 text-sm">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Previous</Button>
          <span>Page {page + 1} of {parcels.data.totalPages}</span>
          <Button variant="outline" size="sm" disabled={page + 1 >= parcels.data.totalPages}
            onClick={() => setPage((current) => current + 1)}>Next</Button>
        </div>
      ) : null}
    </>
  );
}

function ParcelRow({ parcel, couriers, disabled, onAssign }: {
  parcel: Parcel;
  couriers: Courier[];
  disabled: boolean;
  onAssign: (courierId: string) => void;
}) {
  const [courierId, setCourierId] = useState("");
  const suggested = useQuery({
    queryKey: ["dispatch", "suggested", parcel.parcelId],
    queryFn: () => api<Courier>(`/dispatch/parcels/${parcel.parcelId}/suggested-courier`),
    enabled: !parcel.courierId,
  });
  return (
    <div className="rounded-lg border p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div><p className="font-medium">{parcel.trackingNo}</p><p className="text-sm text-muted-foreground">{parcel.pickupCity} → {parcel.dropoffCity}</p></div>
        <span className="rounded-full bg-muted px-2.5 py-1 text-xs">{parcel.status}</span>
      </div>
      {parcel.courierId ? <p className="mt-2 text-sm text-muted-foreground">Assigned courier: {parcel.courierId}</p> : (
        <div className="mt-3 flex gap-2">
          <select aria-label={`Courier for ${parcel.trackingNo}`} value={courierId} onChange={(event) => setCourierId(event.target.value)}
            className="h-8 min-w-48 rounded-lg border bg-background px-2 text-sm" disabled={disabled}>
            <option value="">Select courier</option>
            {couriers.map((courier) => <option key={courier.userId} value={courier.userId}>{courier.fullName} ({courier.activeParcelCount})</option>)}
          </select>
          <Button size="sm" disabled={!courierId || disabled} onClick={() => onAssign(courierId)}>Assign</Button>
          {suggested.data ? <span className="self-center rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs text-emerald-700">Suggested: {suggested.data.fullName}</span> : null}
        </div>
      )}
    </div>
  );
}
