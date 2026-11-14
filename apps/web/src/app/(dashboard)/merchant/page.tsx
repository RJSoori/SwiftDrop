"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import Link from "next/link";
import { PageHeading } from "@/components/dashboard";
import { useLogout, useMe } from "@/lib/session";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { api, ApiError, MerchantOrder, MerchantOrderDetail } from "@/lib/api";
import { SRI_LANKA_CITIES } from "@/lib/cities";
import {
  Package,
  Clock,
  Truck,
  CheckCircle2,
  AlertCircle,
  Search,
  Plus,
  RefreshCw,
  ExternalLink,
  X,
  Loader2,
} from "lucide-react";

const dateFormat = new Intl.DateTimeFormat("en-LK", { dateStyle: "medium", timeStyle: "short" });
const currencyFormat = new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR" });

function getStatusBadgeVariant(status: string): "default" | "secondary" | "outline" | "destructive" {
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

function formatStatus(status: string): string {
  return status.replace(/_/g, " ");
}

export default function MerchantDashboard() {
  const me = useMe();
  const logout = useLogout();
  const queryClient = useQueryClient();
  const status = me.data?.merchantStatus;

  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedTrackingNo, setSelectedTrackingNo] = useState<string | null>(null);

  // New Order Modal State
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [formData, setFormData] = useState({
    orderRef: "",
    pickupAddress: "",
    pickupCity: "Colombo",
    dropoffAddress: "",
    dropoffCity: "Kandy",
    recipientName: "",
    recipientPhone: "",
    weightKg: "1.5",
    serviceType: "STANDARD",
  });

  // Query: Orders list
  const ordersQuery = useQuery({
    queryKey: ["merchant-orders", statusFilter],
    queryFn: () => {
      const url = statusFilter === "ALL" ? "/merchant/orders" : `/merchant/orders?status=${statusFilter}`;
      return api<{ content: MerchantOrder[] }>(url);
    },
    enabled: status === "APPROVED",
  });

  // Query: Selected order detail
  const orderDetailQuery = useQuery({
    queryKey: ["merchant-order-detail", selectedTrackingNo],
    queryFn: () => api<MerchantOrderDetail>(`/merchant/orders/${selectedTrackingNo}`),
    enabled: !!selectedTrackingNo,
  });

  // Mutation: Create Order
  const createOrderMutation = useMutation({
    mutationFn: (payload: typeof formData) =>
      api("/merchant/orders", {
        method: "POST",
        json: {
          orderRef: payload.orderRef || `ORD-${Date.now().toString(36).toUpperCase()}`,
          pickupAddress: payload.pickupAddress,
          pickupCity: payload.pickupCity,
          dropoffAddress: payload.dropoffAddress,
          dropoffCity: payload.dropoffCity,
          recipientName: payload.recipientName,
          recipientPhone: payload.recipientPhone,
          weightKg: parseFloat(payload.weightKg),
          serviceType: payload.serviceType,
        },
      }),
    onSuccess: () => {
      setShowCreateModal(false);
      setFormError(null);
      setFormData({
        orderRef: "",
        pickupAddress: "",
        pickupCity: "Colombo",
        dropoffAddress: "",
        dropoffCity: "Kandy",
        recipientName: "",
        recipientPhone: "",
        weightKg: "1.5",
        serviceType: "STANDARD",
      });
      queryClient.invalidateQueries({ queryKey: ["merchant-orders"] });
    },
    onError: (err: unknown) => {
      setFormError(err instanceof ApiError ? err.message : "Failed to place order. Please check inputs.");
    },
  });

  if (status === "PENDING" || status === "REJECTED" || status === "SUSPENDED") {
    const copy = {
      PENDING: {
        title: "Your application is being reviewed",
        body: "A SwiftDrop admin checks every new merchant's business details. Once your account is approved, log out and log back in to start booking pickups.",
      },
      REJECTED: {
        title: "Your application was not approved",
        body: "We could not verify your business details. Contact SwiftDrop support with your business registration certificate to apply again.",
      },
      SUSPENDED: {
        title: "Your merchant account is suspended",
        body: "Contact SwiftDrop support to restore access.",
      },
    }[status];

    return (
      <div className="max-w-2xl rounded-xl border-2 border-dashed border-tape bg-card p-6 sm:p-8">
        <PageHeading title={copy.title} intro={copy.body} />
        <Button variant="outline" size="lg" className="mt-6" onClick={logout}>
          Log out
        </Button>
      </div>
    );
  }

  const rawOrders = ordersQuery.data?.content || [];

  const filteredOrders = rawOrders.filter((o) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    return (
      o.trackingNo.toLowerCase().includes(q) ||
      (o.orderRef && o.orderRef.toLowerCase().includes(q))
    );
  });

  const counts = {
    total: rawOrders.length,
    created: rawOrders.filter((o) => o.status === "CREATED").length,
    inTransit: rawOrders.filter((o) => ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"].includes(o.status)).length,
    delivered: rawOrders.filter((o) => o.status === "DELIVERED").length,
    failed: rawOrders.filter((o) => o.status === "FAILED").length,
  };

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <PageHeading
          title={`Hello, ${me.data?.name.split(" ")[0] ?? "Merchant"}`}
          intro="Track and dispatch outgoing deliveries for your e-commerce shop."
        />
        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={() => queryClient.invalidateQueries({ queryKey: ["merchant-orders"] })}
            disabled={ordersQuery.isFetching}
          >
            <RefreshCw className={`mr-2 h-4 w-4 ${ordersQuery.isFetching ? "animate-spin" : ""}`} />
            Refresh
          </Button>
          <Button onClick={() => setShowCreateModal(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Book New Delivery
          </Button>
        </div>
      </div>

      {/* Metrics Row */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <Card className="border-2 shadow-sm">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Total Orders</p>
                <p className="text-2xl font-bold tabular-nums font-mono mt-1">{counts.total}</p>
              </div>
              <Package className="h-5 w-5 text-muted-foreground" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-2 shadow-sm">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Awaiting Pickup</p>
                <p className="text-2xl font-bold tabular-nums font-mono mt-1 text-muted-foreground">{counts.created}</p>
              </div>
              <Clock className="h-5 w-5 text-muted-foreground" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-2 shadow-sm">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-muted-foreground">In Transit</p>
                <p className="text-2xl font-bold tabular-nums font-mono mt-1 text-primary">{counts.inTransit}</p>
              </div>
              <Truck className="h-5 w-5 text-primary" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-2 shadow-sm">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Delivered</p>
                <p className="text-2xl font-bold tabular-nums font-mono mt-1 text-emerald-600 dark:text-emerald-400">{counts.delivered}</p>
              </div>
              <CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-2 shadow-sm">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Failed / Returned</p>
                <p className="text-2xl font-bold tabular-nums font-mono mt-1 text-destructive">{counts.failed}</p>
              </div>
              <AlertCircle className="h-5 w-5 text-destructive" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Filter and Search Bar */}
      <Card className="border-2 shadow-sm">
        <CardContent className="pt-6">
          <div className="flex flex-col md:flex-row gap-4 items-center justify-between">
            <div className="relative w-full md:w-80">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search tracking #, reference..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9"
              />
            </div>

            <div className="flex flex-wrap gap-2 w-full md:w-auto">
              {[
                { label: "All", value: "ALL" },
                { label: "Created", value: "CREATED" },
                { label: "In Transit", value: "IN_TRANSIT" },
                { label: "Delivered", value: "DELIVERED" },
                { label: "Failed", value: "FAILED" },
              ].map((f) => (
                <Button
                  key={f.value}
                  variant={statusFilter === f.value ? "default" : "outline"}
                  size="sm"
                  onClick={() => setStatusFilter(f.value)}
                >
                  {f.label}
                </Button>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Orders Table */}
      <Card className="border-2 shadow-sm">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Orders &amp; Shipments</CardTitle>
              <CardDescription>
                {filteredOrders.length} order{filteredOrders.length === 1 ? "" : "s"} found
              </CardDescription>
            </div>
            <Badge variant="outline" className="font-mono text-xs">
              {ordersQuery.data ? `${rawOrders.length} total` : "Loading…"}
            </Badge>
          </div>
        </CardHeader>
        <CardContent>
          {ordersQuery.isPending ? (
            <div className="py-12 text-center text-sm text-muted-foreground">
              <Loader2 className="mx-auto mb-2 h-6 w-6 animate-spin text-muted-foreground/60" />
              Loading merchant orders…
            </div>
          ) : filteredOrders.length === 0 ? (
            <div className="rounded-xl border border-dashed p-10 text-center text-muted-foreground">
              <Package className="mx-auto h-8 w-8 opacity-40 mb-3" />
              <p className="font-medium">No orders found</p>
              <p className="text-sm mt-1">Book a new delivery or simulate purchases using the Demo Store.</p>
              <Button variant="outline" size="sm" className="mt-4" onClick={() => setShowCreateModal(true)}>
                <Plus className="mr-2 h-4 w-4" /> Book First Order
              </Button>
            </div>
          ) : (
            <div className="rounded-xl border bg-card [&_td]:px-4 [&_th]:px-4">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-xs">Tracking #</TableHead>
                    <TableHead className="text-xs">Order Ref</TableHead>
                    <TableHead className="text-xs">Status</TableHead>
                    <TableHead className="text-xs">Fee</TableHead>
                    <TableHead className="text-xs">Date</TableHead>
                    <TableHead className="text-xs text-right">Action</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredOrders.map((order) => (
                    <TableRow key={order.id}>
                      <TableCell className="font-mono text-xs font-semibold text-foreground">
                        {order.trackingNo}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {order.orderRef || "—"}
                      </TableCell>
                      <TableCell>
                        <Badge variant={getStatusBadgeVariant(order.status)} className="text-[10px] uppercase font-semibold">
                          {formatStatus(order.status)}
                        </Badge>
                      </TableCell>
                      <TableCell className="tabular-nums font-mono text-xs">
                        {order.feeLkr ? currencyFormat.format(order.feeLkr) : "—"}
                      </TableCell>
                      <TableCell className="tabular-nums font-mono text-xs text-muted-foreground whitespace-nowrap">
                        {order.createdAt ? dateFormat.format(new Date(order.createdAt)) : "—"}
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setSelectedTrackingNo(order.trackingNo)}
                        >
                          View Details
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Book New Delivery Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-4">
          <Card className="w-full max-w-xl max-h-[90vh] overflow-y-auto shadow-2xl border-2">
            <CardHeader className="flex flex-row items-center justify-between pb-4 border-b">
              <div>
                <CardTitle>Book Outgoing Delivery</CardTitle>
                <CardDescription>Dispatches a delivery job into SwiftDrop parcel network</CardDescription>
              </div>
              <Button variant="ghost" size="icon" onClick={() => setShowCreateModal(false)}>
                <X className="h-4 w-4" />
              </Button>
            </CardHeader>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                createOrderMutation.mutate(formData);
              }}
              className="p-6 space-y-4"
            >
              {formError && (
                <div className="flex items-start gap-2 rounded-md bg-destructive/10 p-3 text-xs text-destructive">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>{formError}</span>
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="orderRef" className="text-xs">Store Order Reference</Label>
                  <Input
                    id="orderRef"
                    placeholder="e.g. SHOP-88219"
                    value={formData.orderRef}
                    onChange={(e) => setFormData({ ...formData, orderRef: e.target.value })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="weightKg" className="text-xs">Parcel Weight (kg)</Label>
                  <Input
                    id="weightKg"
                    type="number"
                    step="0.1"
                    min="0.1"
                    required
                    value={formData.weightKg}
                    onChange={(e) => setFormData({ ...formData, weightKg: e.target.value })}
                  />
                </div>
              </div>

              {/* Pickup Address */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="md:col-span-2 space-y-1.5">
                  <Label htmlFor="pickupAddress" className="text-xs">Pickup Address</Label>
                  <Input
                    id="pickupAddress"
                    placeholder="Merchant Warehouse Address"
                    required
                    value={formData.pickupAddress}
                    onChange={(e) => setFormData({ ...formData, pickupAddress: e.target.value })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="pickupCity" className="text-xs">Pickup City</Label>
                  <select
                    id="pickupCity"
                    value={formData.pickupCity}
                    onChange={(e) => setFormData({ ...formData, pickupCity: e.target.value })}
                    className="mt-1 flex h-8 w-full rounded-lg border border-input bg-card text-foreground px-2 text-xs outline-none focus-visible:border-ring [&>option]:bg-card [&>option]:text-foreground"
                  >
                    {SRI_LANKA_CITIES.map((c) => (
                      <option key={`pickup-${c.name}`} value={c.name} className="bg-card text-foreground">
                        {c.name} ({c.province})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Dropoff Address */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="md:col-span-2 space-y-1.5">
                  <Label htmlFor="dropoffAddress" className="text-xs">Destination Address</Label>
                  <Input
                    id="dropoffAddress"
                    placeholder="Customer destination address"
                    required
                    value={formData.dropoffAddress}
                    onChange={(e) => setFormData({ ...formData, dropoffAddress: e.target.value })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="dropoffCity" className="text-xs">Destination City</Label>
                  <select
                    id="dropoffCity"
                    value={formData.dropoffCity}
                    onChange={(e) => setFormData({ ...formData, dropoffCity: e.target.value })}
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

              {/* Customer Contact */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="recipientName" className="text-xs">Recipient Name</Label>
                  <Input
                    id="recipientName"
                    placeholder="Full Customer Name"
                    required
                    value={formData.recipientName}
                    onChange={(e) => setFormData({ ...formData, recipientName: e.target.value })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="recipientPhone" className="text-xs">Recipient Phone</Label>
                  <Input
                    id="recipientPhone"
                    placeholder="07XXXXXXXX"
                    required
                    value={formData.recipientPhone}
                    onChange={(e) => setFormData({ ...formData, recipientPhone: e.target.value })}
                  />
                </div>
              </div>

              {/* Service Type */}
              <div className="space-y-1.5">
                <Label htmlFor="serviceType" className="text-xs">Service Type</Label>
                <select
                  id="serviceType"
                  value={formData.serviceType}
                  onChange={(e) => setFormData({ ...formData, serviceType: e.target.value })}
                  className="mt-1 flex h-8 w-full rounded-lg border border-input bg-card text-foreground px-2 text-xs outline-none focus-visible:border-ring [&>option]:bg-card [&>option]:text-foreground"
                >
                  <option value="STANDARD" className="bg-card text-foreground">Standard</option>
                  <option value="EXPRESS" className="bg-card text-foreground">Express (Priority)</option>
                </select>
              </div>

              <div className="flex justify-end gap-2 pt-4 border-t">
                <Button type="button" variant="outline" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </Button>
                <Button type="submit" disabled={createOrderMutation.isPending}>
                  {createOrderMutation.isPending ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Booking...
                    </>
                  ) : (
                    "Confirm & Dispatch"
                  )}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* View Order Detail Modal */}
      {selectedTrackingNo && (
        <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-4">
          <Card className="w-full max-w-lg shadow-2xl border-2">
            <CardHeader className="flex flex-row items-center justify-between pb-4 border-b">
              <div>
                <CardTitle>Shipment Details</CardTitle>
                <CardDescription className="tabular-nums font-mono">{selectedTrackingNo}</CardDescription>
              </div>
              <Button variant="ghost" size="icon" onClick={() => setSelectedTrackingNo(null)}>
                <X className="h-4 w-4" />
              </Button>
            </CardHeader>
            <CardContent className="p-6">
              {orderDetailQuery.isPending ? (
                <div className="py-6 text-center text-sm text-muted-foreground">
                  <Loader2 className="mx-auto mb-2 h-6 w-6 animate-spin text-muted-foreground/60" />
                  Loading shipment details…
                </div>
              ) : orderDetailQuery.data ? (
                <div className="space-y-4">
                  <div className="flex items-center justify-between pb-3 border-b">
                    <div>
                      <p className="text-sm text-muted-foreground">Order Status</p>
                      <div className="mt-1">
                        <Badge variant={getStatusBadgeVariant(orderDetailQuery.data.status)} className="text-[10px] uppercase font-semibold">
                          {formatStatus(orderDetailQuery.data.status)}
                        </Badge>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-sm text-muted-foreground">Order Reference</p>
                      <p className="font-semibold text-sm">{orderDetailQuery.data.orderRef || "N/A"}</p>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <p className="text-muted-foreground">Pickup Location</p>
                      <p className="font-medium mt-0.5">{orderDetailQuery.data.pickupCity || "Colombo"}</p>
                      <p className="text-xs text-muted-foreground">{orderDetailQuery.data.pickupAddress || "Warehouse"}</p>
                    </div>
                    <div>
                      <p className="text-muted-foreground">Dropoff Location</p>
                      <p className="font-medium mt-0.5">{orderDetailQuery.data.dropoffCity || "Kandy"}</p>
                      <p className="text-xs text-muted-foreground">{orderDetailQuery.data.dropoffAddress || "Customer address"}</p>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4 text-sm pt-2 border-t">
                    <div>
                      <p className="text-muted-foreground">Recipient</p>
                      <p className="font-medium mt-0.5">{orderDetailQuery.data.recipientName || "Valued Customer"}</p>
                    </div>
                    <div>
                      <p className="text-muted-foreground">Delivery Fee</p>
                      <p className="font-medium mt-0.5 tabular-nums font-mono">
                        {orderDetailQuery.data.feeLkr ? currencyFormat.format(orderDetailQuery.data.feeLkr) : "—"}
                      </p>
                    </div>
                  </div>

                  {orderDetailQuery.data.history && orderDetailQuery.data.history.length > 0 && (
                    <div className="pt-3 border-t">
                      <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">Tracking History</p>
                      <div className="space-y-2 max-h-40 overflow-y-auto pr-1">
                        {orderDetailQuery.data.history.map((ev, i) => (
                          <div key={i} className="text-xs flex justify-between items-center py-1 border-b last:border-b-0">
                            <div>
                              <span className="font-medium">{formatStatus(ev.toStatus)}</span>
                              {ev.note && <span className="text-muted-foreground ml-1.5">— {ev.note}</span>}
                            </div>
                            <span className="text-muted-foreground tabular-nums font-mono">{dateFormat.format(new Date(ev.occurredAt))}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  <div className="pt-4 border-t flex justify-between items-center">
                    <Link
                      href={`/?track=${selectedTrackingNo}`}
                      target="_blank"
                      className="text-xs text-primary hover:underline flex items-center gap-1 font-medium"
                    >
                      Public Tracking Page <ExternalLink className="h-3 w-3" />
                    </Link>
                    <Button variant="outline" size="sm" onClick={() => setSelectedTrackingNo(null)}>
                      Close
                    </Button>
                  </div>
                </div>
              ) : (
                <p className="text-destructive text-sm">Failed to load order details.</p>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
