"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  api,
  ApiError,
  type CreateParcelRequest,
  type ParcelResponse,
  type ParcelStatus,
  type QuoteRequest,
  type QuoteResponse,
} from "@/lib/api";
import { useMe } from "@/lib/session";
import {
  Package,
  Truck,
  ArrowRight,
  Calculator,
  CheckCircle2,
  AlertCircle,
  Clock,
} from "lucide-react";

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

export default function CustomerDashboard() {
  const me = useMe();
  const queryClient = useQueryClient();

  // Booking Form State
  const [pickupAddress, setPickupAddress] = useState("");
  const [dropoffAddress, setDropoffAddress] = useState("");
  const [weightKg, setWeightKg] = useState("1.5");
  const [size, setSize] = useState("MEDIUM");
  const [strategy, setStrategy] = useState<"WEIGHT_TIERED" | "FLAT_RATE" | "EXPRESS">("WEIGHT_TIERED");
  const [lengthCm, setLengthCm] = useState("20");
  const [widthCm, setWidthCm] = useState("15");
  const [heightCm, setHeightCm] = useState("10");

  const [quote, setQuote] = useState<QuoteResponse | null>(null);
  const [bookingSuccess, setBookingSuccess] = useState<ParcelResponse | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  // Queries & Mutations
  const parcelsQuery = useQuery({
    queryKey: ["my-parcels"],
    queryFn: () => api<ParcelResponse[]>("/parcels/my-parcels"),
  });

  const quoteMutation = useMutation({
    mutationFn: (req: QuoteRequest) =>
      api<QuoteResponse>("/parcels/quote", {
        method: "POST",
        json: req,
      }),
    onSuccess: (data) => {
      setQuote(data);
      setFormError(null);
    },
    onError: () => {
      setFormError("Could not calculate delivery quote. Please check the values entered.");
    },
  });

  const bookParcelMutation = useMutation({
    mutationFn: (req: CreateParcelRequest) =>
      api<ParcelResponse>("/parcels", {
        method: "POST",
        json: req,
      }),
    onSuccess: (newParcel) => {
      setBookingSuccess(newParcel);
      setFormError(null);
      // Reset form fields
      setPickupAddress("");
      setDropoffAddress("");
      setQuote(null);
      return queryClient.invalidateQueries({ queryKey: ["my-parcels"] });
    },
    onError: (err) => {
      setFormError(err instanceof ApiError ? err.message : "Failed to book parcel. Please try again.");
    },
  });

  function handleCalculateQuote() {
    setFormError(null);
    setBookingSuccess(null);
    const w = parseFloat(weightKg);
    const l = parseFloat(lengthCm);
    const wi = parseFloat(widthCm);
    const h = parseFloat(heightCm);

    if (isNaN(w) || w <= 0) {
      setFormError("Please enter a valid weight in kg.");
      return;
    }

    quoteMutation.mutate({
      weightKg: w,
      lengthCm: isNaN(l) ? 20 : l,
      widthCm: isNaN(wi) ? 15 : wi,
      heightCm: isNaN(h) ? 10 : h,
      strategy,
    });
  }

  function handleBookParcel(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setBookingSuccess(null);

    if (!pickupAddress.trim() || !dropoffAddress.trim()) {
      setFormError("Pickup address and dropoff address are required.");
      return;
    }

    const w = parseFloat(weightKg);
    if (isNaN(w) || w <= 0) {
      setFormError("Please enter a valid weight.");
      return;
    }

    bookParcelMutation.mutate({
      pickupAddress: pickupAddress.trim(),
      dropoffAddress: dropoffAddress.trim(),
      weightKg: w,
      lengthCm: parseFloat(lengthCm) || 20,
      widthCm: parseFloat(widthCm) || 15,
      heightCm: parseFloat(heightCm) || 10,
      size,
      strategy,
    });
  }

  return (
    <div className="space-y-8">
      <PageHeading
        title={`Hello, ${me.data?.name.split(" ")[0] ?? "Customer"}`}
        intro="Calculate delivery fees, book island-wide courier shipments, and track your active parcels in real time."
      />

      <div className="grid gap-8 lg:grid-cols-12">
        {/* Left Column: Book a Parcel & Quote Estimator */}
        <div className="lg:col-span-5 space-y-6">
          <Card className="border-2 shadow-sm">
            <CardHeader>
              <div className="flex items-center gap-2">
                <Package className="h-5 w-5 text-brand" />
                <CardTitle>Book a Delivery</CardTitle>
              </div>
              <CardDescription>
                Enter parcel details to estimate fees and schedule a delivery pickup.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleBookParcel} className="space-y-4">
                {formError && (
                  <div className="flex items-start gap-2 rounded-md bg-destructive/10 p-3 text-xs text-destructive">
                    <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                    <span>{formError}</span>
                  </div>
                )}

                {bookingSuccess && (
                  <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-xs space-y-2">
                    <div className="flex items-center gap-2 font-semibold text-emerald-800 dark:text-emerald-300">
                      <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                      <span>Delivery Successfully Booked!</span>
                    </div>
                    <p className="text-muted-foreground">
                      Waybill Number:{" "}
                      <span className="font-mono font-bold text-foreground">{bookingSuccess.trackingNo}</span>
                    </p>
                    <p className="text-muted-foreground">
                      Fee: <span className="font-semibold text-foreground">LKR {Number(bookingSuccess.feeLkr).toFixed(2)}</span>
                    </p>
                  </div>
                )}

                <div>
                  <Label htmlFor="pickup" className="text-xs">Pickup Address</Label>
                  <Input
                    id="pickup"
                    required
                    placeholder="e.g. 142 Galle Road, Colombo 03"
                    value={pickupAddress}
                    onChange={(e) => setPickupAddress(e.target.value)}
                    className="mt-1"
                  />
                </div>

                <div>
                  <Label htmlFor="dropoff" className="text-xs">Dropoff Address</Label>
                  <Input
                    id="dropoff"
                    required
                    placeholder="e.g. 88 Peradeniya Road, Kandy"
                    value={dropoffAddress}
                    onChange={(e) => setDropoffAddress(e.target.value)}
                    className="mt-1"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <Label htmlFor="weight" className="text-xs">Weight (kg)</Label>
                    <Input
                      id="weight"
                      type="number"
                      step="0.1"
                      min="0.1"
                      required
                      value={weightKg}
                      onChange={(e) => {
                        setWeightKg(e.target.value);
                        setQuote(null);
                      }}
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <Label htmlFor="size" className="text-xs">Package Size</Label>
                    <select
                      id="size"
                      value={size}
                      onChange={(e) => setSize(e.target.value)}
                      className="mt-1 flex h-8 w-full rounded-lg border border-input bg-transparent px-2 text-xs outline-none focus-visible:border-ring"
                    >
                      <option value="SMALL">Small (Document / Pouch)</option>
                      <option value="MEDIUM">Medium (Box &lt; 5kg)</option>
                      <option value="LARGE">Large (Heavy crate)</option>
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <Label htmlFor="length" className="text-[11px] text-muted-foreground">Length (cm)</Label>
                    <Input
                      id="length"
                      type="number"
                      value={lengthCm}
                      onChange={(e) => setLengthCm(e.target.value)}
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <Label htmlFor="width" className="text-[11px] text-muted-foreground">Width (cm)</Label>
                    <Input
                      id="width"
                      type="number"
                      value={widthCm}
                      onChange={(e) => setWidthCm(e.target.value)}
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <Label htmlFor="height" className="text-[11px] text-muted-foreground">Height (cm)</Label>
                    <Input
                      id="height"
                      type="number"
                      value={heightCm}
                      onChange={(e) => setHeightCm(e.target.value)}
                      className="mt-1"
                    />
                  </div>
                </div>

                <div>
                  <Label htmlFor="strategy" className="text-xs">Delivery Tier</Label>
                  <select
                    id="strategy"
                    value={strategy}
                    onChange={(e) => {
                      setStrategy(e.target.value as "WEIGHT_TIERED" | "FLAT_RATE" | "EXPRESS");
                      setQuote(null);
                    }}
                    className="mt-1 flex h-8 w-full rounded-lg border border-input bg-transparent px-2 text-xs outline-none focus-visible:border-ring"
                  >
                    <option value="WEIGHT_TIERED">Standard (Weight-tiered rates)</option>
                    <option value="FLAT_RATE">Flat Rate (Fixed fee)</option>
                    <option value="EXPRESS">Express Priority (1.5x speed multiplier)</option>
                  </select>
                </div>

                {/* Quote Preview Box */}
                {quote && (
                  <div className="rounded-lg bg-muted/60 p-3 text-xs space-y-1.5 border">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Actual Weight:</span>
                      <span className="font-medium">{quote.actualWeightKg} kg</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Volumetric Weight:</span>
                      <span className="font-medium">{quote.volumetricWeightKg} kg</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Chargeable Weight:</span>
                      <span className="font-semibold">{quote.chargeableWeightKg} kg</span>
                    </div>
                    <div className="flex justify-between border-t pt-1.5 font-bold text-sm text-foreground">
                      <span>Estimated Fee:</span>
                      <span className="text-brand">LKR {Number(quote.feeLkr).toFixed(2)}</span>
                    </div>
                  </div>
                )}

                <div className="flex gap-2 pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleCalculateQuote}
                    disabled={quoteMutation.isPending}
                    className="flex-1 text-xs"
                  >
                    <Calculator className="mr-1.5 h-3.5 w-3.5" />
                    {quoteMutation.isPending ? "Calculating…" : "Get Quote"}
                  </Button>
                  <Button
                    type="submit"
                    size="sm"
                    disabled={bookParcelMutation.isPending}
                    className="flex-1 text-xs"
                  >
                    <Truck className="mr-1.5 h-3.5 w-3.5" />
                    {bookParcelMutation.isPending ? "Booking…" : "Book Parcel"}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Active Deliveries / My Parcels */}
        <div className="lg:col-span-7 space-y-4">
          <Card className="border-2 shadow-sm">
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>My Deliveries</CardTitle>
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
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {parcelsQuery.data.map((p) => (
                      <TableRow key={p.id}>
                        <TableCell className="font-mono text-xs font-semibold text-foreground">
                          {p.trackingNo}
                        </TableCell>
                        <TableCell>
                          <Badge variant={getStatusBadgeVariant(p.currentStatus)} className="text-[10px] uppercase font-semibold">
                            {formatStatus(p.currentStatus)}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground">
                          <div className="flex items-center gap-1">
                            <span className="truncate max-w-[100px] text-foreground font-medium">{p.pickupAddress}</span>
                            <ArrowRight className="h-3 w-3 shrink-0" />
                            <span className="truncate max-w-[100px]">{p.dropoffAddress}</span>
                          </div>
                        </TableCell>
                        <TableCell className="text-xs font-semibold text-right tabular">
                          {Number(p.feeLkr).toFixed(2)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <div className="rounded-xl border border-dashed py-12 text-center text-sm text-muted-foreground">
                  <Package className="mx-auto mb-2 h-8 w-8 text-muted-foreground/40" />
                  No parcels booked yet. Use the booking form on the left to schedule your first delivery!
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
