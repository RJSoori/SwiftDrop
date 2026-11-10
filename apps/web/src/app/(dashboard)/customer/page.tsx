"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import Link from "next/link";
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
  QrCode,
  FileText,
  ExternalLink,
  RotateCcw,
} from "lucide-react";
import { SRI_LANKA_CITIES } from "@/lib/cities";

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
  const [pickupCity, setPickupCity] = useState("Colombo");
  const [pickupAddress, setPickupAddress] = useState("");
  const [dropoffCity, setDropoffCity] = useState("Kandy");
  const [dropoffAddress, setDropoffAddress] = useState("");
  const [recipientName, setRecipientName] = useState("");
  const [recipientPhone, setRecipientPhone] = useState("");

  const [weightKg, setWeightKg] = useState("1.5");
  const [size, setSize] = useState("MEDIUM");
  const [serviceType, setServiceType] = useState<"STANDARD" | "EXPRESS">("STANDARD");
  const strategy = serviceType === "EXPRESS" ? "EXPRESS" : "WEIGHT_TIERED";
  const [lengthCm, setLengthCm] = useState("20");
  const [widthCm, setWidthCm] = useState("15");
  const [heightCm, setHeightCm] = useState("10");

  const [quote, setQuote] = useState<QuoteResponse | null>(null);
  const [bookingSuccess, setBookingSuccess] = useState<ParcelResponse | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  // Queries & Mutations
  const parcelsQuery = useQuery({
    queryKey: ["my-parcels"],
    queryFn: () => api<ParcelResponse[]>("/parcels/mine"),
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
      return queryClient.invalidateQueries({ queryKey: ["my-parcels"] });
    },
    onError: (err) => {
      setFormError(err instanceof ApiError ? err.message : "Failed to book parcel. Please try again.");
    },
  });

  function handleCalculateQuote() {
    setFormError(null);
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
      strategy: serviceType === "EXPRESS" ? "EXPRESS" : strategy,
    });
  }

  function handleBookParcel(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);

    if (!pickupAddress.trim() || !dropoffAddress.trim()) {
      setFormError("Pickup address and dropoff address are required.");
      return;
    }

    const w = parseFloat(weightKg);
    if (isNaN(w) || w <= 0) {
      setFormError("Please enter a valid weight.");
      return;
    }

    const pickupGeo = SRI_LANKA_CITIES.find((c) => c.name === pickupCity);
    const dropoffGeo = SRI_LANKA_CITIES.find((c) => c.name === dropoffCity);

    bookParcelMutation.mutate({
      pickupCity,
      pickupAddress: pickupAddress.trim(),
      pickupLat: pickupGeo?.lat,
      pickupLng: pickupGeo?.lng,
      dropoffCity,
      dropoffAddress: dropoffAddress.trim(),
      dropoffLat: dropoffGeo?.lat,
      dropoffLng: dropoffGeo?.lng,
      recipientName: recipientName.trim() || "Recipient",
      recipientPhone: recipientPhone.trim() || "+94770000000",
      serviceType,
      weightKg: w,
      lengthCm: parseFloat(lengthCm) || 20,
      widthCm: parseFloat(widthCm) || 15,
      heightCm: parseFloat(heightCm) || 10,
      size,
      strategy: serviceType === "EXPRESS" ? "EXPRESS" : strategy,
    });
  }

  function resetBookingForm() {
    setBookingSuccess(null);
    setPickupAddress("");
    setDropoffAddress("");
    setRecipientName("");
    setRecipientPhone("");
    setQuote(null);
    setFormError(null);
  }

  return (
    <div className="space-y-8">
      <PageHeading
        title={`Hello, ${me.data?.name.split(" ")[0] ?? "Customer"}`}
        intro="Calculate delivery fees, book island-wide courier shipments, and track your active parcels in real time."
      />

      <div className="grid gap-8 lg:grid-cols-12">
        {/* Left Column: Book a Parcel or Booking Confirmation */}
        <div className="lg:col-span-5 space-y-6">
          {bookingSuccess ? (
            /* Confirmation Screen with QR code, Track & Receipt links */
            <Card className="border-2 border-emerald-500/40 bg-card shadow-md">
              <CardHeader className="bg-emerald-500/10 pb-4">
                <div className="flex items-center gap-2 text-emerald-700 dark:text-emerald-400">
                  <CheckCircle2 className="h-6 w-6 shrink-0" />
                  <CardTitle className="text-lg">Delivery Confirmed!</CardTitle>
                </div>
                <CardDescription className="text-xs">
                  Your parcel booking has been recorded. Courier assignment is in progress.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-5 pt-5">
                <div className="rounded-lg bg-muted/60 p-4 text-center border">
                  <span className="text-xs text-muted-foreground uppercase font-semibold tracking-wider">
                    Waybill Tracking Number
                  </span>
                  <div className="mt-1 font-mono text-xl font-bold text-foreground tracking-wide">
                    {bookingSuccess.trackingNo}
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">
                    Fee: <span className="font-semibold text-foreground">LKR {Number(bookingSuccess.feeLkr).toFixed(2)}</span>
                  </div>
                </div>

                {/* QR Code Container */}
                <div className="flex flex-col items-center justify-center p-3 rounded-xl border bg-white dark:bg-zinc-900 shadow-inner">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={`https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(
                      typeof window !== "undefined"
                        ? `${window.location.origin}/?track=${bookingSuccess.trackingNo}`
                        : `https://swiftdrop.lk/?track=${bookingSuccess.trackingNo}`
                    )}`}
                    alt={`Tracking QR Code for ${bookingSuccess.trackingNo}`}
                    width={150}
                    height={150}
                    className="rounded-md border p-1"
                  />
                  <span className="mt-2 text-[11px] text-muted-foreground flex items-center gap-1">
                    <QrCode className="h-3.5 w-3.5 text-brand" /> Scan with mobile phone to track live
                  </span>
                </div>

                {/* Quick Navigation / Action Links */}
                <div className="space-y-2 pt-1">
                  <Link
                    href={`/?track=${bookingSuccess.trackingNo}`}
                    className="flex items-center justify-center gap-2 w-full rounded-lg bg-brand px-4 py-2.5 text-xs font-semibold text-brand-foreground shadow hover:bg-brand/90 transition-colors"
                  >
                    <ExternalLink className="h-3.5 w-3.5" />
                    Open Live Tracking Page
                  </Link>

                  <a
                    href={`/api/v1/parcels/${bookingSuccess.id}/receipt`}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center justify-center gap-2 w-full rounded-lg border border-input bg-background px-4 py-2 text-xs font-medium text-foreground hover:bg-accent transition-colors"
                  >
                    <FileText className="h-3.5 w-3.5 text-muted-foreground" />
                    Download Receipt PDF (M7)
                  </a>

                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={resetBookingForm}
                    className="w-full text-xs text-muted-foreground hover:text-foreground"
                  >
                    <RotateCcw className="mr-1.5 h-3.5 w-3.5" />
                    Book Another Parcel
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : (
            /* Booking Form */
            <Card className="border-2 shadow-sm">
              <CardHeader>
                <div className="flex items-center gap-2">
                  <Package className="h-5 w-5 text-brand" />
                  <CardTitle>Book a Delivery</CardTitle>
                </div>
                <CardDescription>
                  Enter pickup & dropoff cities, dimensions, and recipient details for instant quote and booking.
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

                  {/* Pickup City & Address */}
                  <div className="space-y-2">
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <Label htmlFor="pickupCity" className="text-xs">Pickup City</Label>
                        <select
                          id="pickupCity"
                          value={pickupCity}
                          onChange={(e) => setPickupCity(e.target.value)}
                          className="mt-1 flex h-8 w-full rounded-lg border border-input bg-card text-foreground px-2 text-xs outline-none focus-visible:border-ring [&>option]:bg-card [&>option]:text-foreground"
                        >
                          {SRI_LANKA_CITIES.map((c) => (
                            <option key={`pickup-${c.name}`} value={c.name} className="bg-card text-foreground">
                              {c.name} ({c.province})
                            </option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <Label htmlFor="dropoffCity" className="text-xs">Dropoff City</Label>
                        <select
                          id="dropoffCity"
                          value={dropoffCity}
                          onChange={(e) => setDropoffCity(e.target.value)}
                          className="mt-1 flex h-8 w-full rounded-lg border border-input bg-card text-foreground px-2 text-xs outline-none focus-visible:border-ring [&>option]:bg-card [&>option]:text-foreground"
                        >
                          {SRI_LANKA_CITIES.map((c) => (
                            <option key={`dropoff-${c.name}`} value={c.name} className="bg-card text-foreground">
                              {c.name} ({c.province})
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>

                    <div>
                      <Label htmlFor="pickupAddress" className="text-xs">Pickup Address</Label>
                      <Input
                        id="pickupAddress"
                        required
                        placeholder="e.g. 142 Galle Road, Colombo 03"
                        value={pickupAddress}
                        onChange={(e) => setPickupAddress(e.target.value)}
                        className="mt-1"
                      />
                    </div>

                    <div>
                      <Label htmlFor="dropoffAddress" className="text-xs">Dropoff Address</Label>
                      <Input
                        id="dropoffAddress"
                        required
                        placeholder="e.g. 88 Peradeniya Road, Kandy"
                        value={dropoffAddress}
                        onChange={(e) => setDropoffAddress(e.target.value)}
                        className="mt-1"
                      />
                    </div>
                  </div>

                  {/* Recipient Contact */}
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <Label htmlFor="recipientName" className="text-xs">Recipient Name</Label>
                      <Input
                        id="recipientName"
                        required
                        placeholder="e.g. Kasun Silva"
                        value={recipientName}
                        onChange={(e) => setRecipientName(e.target.value)}
                        className="mt-1"
                      />
                    </div>
                    <div>
                      <Label htmlFor="recipientPhone" className="text-xs">Recipient Phone</Label>
                      <Input
                        id="recipientPhone"
                        required
                        placeholder="e.g. 0771234567"
                        value={recipientPhone}
                        onChange={(e) => setRecipientPhone(e.target.value)}
                        className="mt-1"
                      />
                    </div>
                  </div>

                  {/* Weight, Size & Service Type */}
                  <div className="grid grid-cols-3 gap-2">
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
                      <Label htmlFor="size" className="text-xs">Size</Label>
                      <select
                        id="size"
                        value={size}
                        onChange={(e) => setSize(e.target.value)}
                        className="mt-1 flex h-8 w-full rounded-lg border border-input bg-card text-foreground px-2 text-xs outline-none focus-visible:border-ring [&>option]:bg-card [&>option]:text-foreground"
                      >
                        <option value="SMALL" className="bg-card text-foreground">Small</option>
                        <option value="MEDIUM" className="bg-card text-foreground">Medium</option>
                        <option value="LARGE" className="bg-card text-foreground">Large</option>
                      </select>
                    </div>
                    <div>
                      <Label htmlFor="serviceType" className="text-xs">Service Type</Label>
                      <select
                        id="serviceType"
                        value={serviceType}
                        onChange={(e) => {
                          setServiceType(e.target.value as "STANDARD" | "EXPRESS");
                          setQuote(null);
                        }}
                        className="mt-1 flex h-8 w-full rounded-lg border border-input bg-card text-foreground px-2 text-xs outline-none focus-visible:border-ring [&>option]:bg-card [&>option]:text-foreground"
                      >
                        <option value="STANDARD" className="bg-card text-foreground">Standard</option>
                        <option value="EXPRESS" className="bg-card text-foreground">Express (Priority)</option>
                      </select>
                    </div>
                  </div>

                  {/* Dimensions */}
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

                  {/* Quote Breakdown Preview */}
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
                      {bookParcelMutation.isPending ? "Booking…" : "Confirm & Book"}
                    </Button>
                  </div>
                </form>
              </CardContent>
            </Card>
          )}
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
                          <Link href={`/?track=${p.trackingNo}`} className="hover:underline text-brand">
                            {p.trackingNo}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <Badge variant={getStatusBadgeVariant(p.currentStatus)} className="text-[10px] uppercase font-semibold">
                            {formatStatus(p.currentStatus)}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground">
                          <div className="flex items-center gap-1">
                            <span className="truncate max-w-[100px] text-foreground font-medium">
                              {p.pickupCity || p.pickupAddress}
                            </span>
                            <ArrowRight className="h-3 w-3 shrink-0" />
                            <span className="truncate max-w-[100px]">
                              {p.dropoffCity || p.dropoffAddress}
                            </span>
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
