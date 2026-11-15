"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { ArrowRight, Clock, ExternalLink, FileText, Package, QrCode } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, type ParcelResponse, type ParcelStatus, type TrackingView } from "@/lib/api";
import { useLiveUpdates } from "@/lib/realtime";

function getStatusBadgeVariant(status: ParcelStatus) {
  switch (status) {
    case "DELIVERED":
      return "default";
    case "IN_TRANSIT":
    case "OUT_FOR_DELIVERY":
    case "PICKED_UP":
      return "secondary";
    case "FAILED":
    case "RETURNED":
      return "destructive";
    default:
      return "outline";
  }
}

function formatStatus(status: ParcelStatus): string {
  return status.replace(/_/g, " ");
}

function CustomerParcelRow({ parcel, tracking }: { parcel: ParcelResponse; tracking?: TrackingView }) {
  const currentStatus = tracking?.currentStatus ?? parcel.currentStatus;

  return (
    <TableRow>
      <TableCell className="font-mono text-xs font-semibold text-foreground">
        <Link href={`/track/${encodeURIComponent(parcel.trackingNo)}`} className="text-brand hover:underline">
          {parcel.trackingNo}
        </Link>
      </TableCell>
      <TableCell>
        <Badge variant={getStatusBadgeVariant(currentStatus)} className="text-[10px] uppercase font-semibold">
          {formatStatus(currentStatus)}
        </Badge>
      </TableCell>
      <TableCell className="text-xs text-muted-foreground">
        <div className="flex items-center gap-1">
          <span className="truncate max-w-[100px] text-foreground font-medium">
            {parcel.pickupCity || parcel.pickupAddress}
          </span>
          <ArrowRight className="h-3 w-3 shrink-0" />
          <span className="truncate max-w-[100px]">
            {parcel.dropoffCity || parcel.dropoffAddress}
          </span>
        </div>
      </TableCell>
      <TableCell className="text-xs font-semibold text-right tabular">
        {Number(parcel.feeLkr).toFixed(2)}
      </TableCell>
      <TableCell className="text-right">
        <div className="flex justify-end gap-2">
          <Link href={`/track/${encodeURIComponent(parcel.trackingNo)}`} aria-label={`Track ${parcel.trackingNo}`}>
            <ExternalLink className="h-4 w-4 text-brand" />
          </Link>
          <a href={`/api/v1/parcels/${parcel.id}/receipt.pdf`} aria-label={`Download receipt for ${parcel.trackingNo}`}>
            <FileText className="h-4 w-4 text-muted-foreground" />
          </a>
          <a href={`/api/v1/parcels/${parcel.id}/label.pdf`} aria-label={`Download label for ${parcel.trackingNo}`}>
            <QrCode className="h-4 w-4 text-muted-foreground" />
          </a>
        </div>
      </TableCell>
    </TableRow>
  );
}

/** The customer's own parcels, with live status and links to tracking, the receipt and the label. */
export function MyDeliveries() {
  const parcelsQuery = useQuery({
    queryKey: ["my-parcels"],
    queryFn: () => api<ParcelResponse[]>("/parcels/mine"),
  });
  const parcelTopics = parcelsQuery.data?.map((parcel) => `/topic/parcels/${parcel.id}`) ?? [];
  const liveUpdates = useLiveUpdates<TrackingView>(parcelTopics);

  return (
    <Card className="border-2 shadow-sm">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>Booked parcels</CardTitle>
            <CardDescription>
              Live tracking and delivery status of all parcels booked under your account.
            </CardDescription>
          </div>
          <Badge variant="outline" className="font-mono text-xs">
            {parcelsQuery.data ? `${parcelsQuery.data.length} total` : "Loading…"}
          </Badge>
        </div>
      </CardHeader>
      <CardContent>
        {parcelsQuery.isPending ? (
          <div className="py-12 text-center text-sm text-muted-foreground">
            <Clock className="mx-auto mb-2 h-6 w-6 animate-spin text-muted-foreground/60" />
            Loading your parcels…
          </div>
        ) : parcelsQuery.data && parcelsQuery.data.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="text-xs">Tracking No</TableHead>
                <TableHead className="text-xs">Status</TableHead>
                <TableHead className="text-xs">Route</TableHead>
                <TableHead className="text-xs text-right">Fee (LKR)</TableHead>
                <TableHead className="text-xs text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {parcelsQuery.data.map((parcel) => (
                <CustomerParcelRow
                  key={parcel.id}
                  parcel={parcel}
                  tracking={liveUpdates.data[`/topic/parcels/${parcel.id}`]}
                />
              ))}
            </TableBody>
          </Table>
        ) : (
          <div className="rounded-xl border border-dashed py-12 text-center text-sm text-muted-foreground">
            <Package className="mx-auto mb-2 h-8 w-8 text-muted-foreground/40" />
            No parcels booked yet.{" "}
            <Link href="/customer" className="font-medium text-brand hover:underline">
              Book your first delivery
            </Link>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
