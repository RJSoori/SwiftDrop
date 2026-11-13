"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useMe } from "@/lib/session";
import {
  money,
  orderStatuses,
  statusLabel,
  type OrderPage,
} from "@/lib/merchant";
import { Button } from "@/components/ui/button";

export function MerchantOrders() {
  const me = useMe();
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const orders = useQuery({
    queryKey: ["merchant-orders", status, page],
    enabled: me.data?.merchantStatus === "APPROVED",
    queryFn: () =>
      api<OrderPage>(
        "/merchant/orders?page=" +
          page +
          "&size=20" +
          (status ? "&status=" + status : ""),
      ),
    refetchInterval: 10000,
    retry: 1,
  });
  const summary = useQuery({
    queryKey: ["merchant-order-summary"],
    enabled: me.data?.merchantStatus === "APPROVED",
    queryFn: () => api<Record<string, number>>("/merchant/orders/summary"),
    refetchInterval: 10000,
    retry: 1,
  });
  return (
    <section className="mt-8 space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          [
            "Total orders",
            Object.values(summary.data ?? {}).reduce((a, b) => a + b, 0),
          ],
          ["Delivered", summary.data?.DELIVERED ?? 0],
          [
            "On the way",
            (summary.data?.PICKED_UP ?? 0) +
              (summary.data?.IN_TRANSIT ?? 0) +
              (summary.data?.OUT_FOR_DELIVERY ?? 0),
          ],
          ["Failed", summary.data?.FAILED ?? 0],
        ].map(([label, count]) => (
          <div key={label} className="rounded-xl border bg-card p-5">
            <p className="text-sm text-muted-foreground">{label}</p>
            <p className="mt-2 text-3xl font-semibold tabular-nums">
              {summary.isPending || summary.isError ? "—" : count}
            </p>
          </div>
        ))}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold">Your orders</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Updates every 10 seconds. Create orders through your shop
            integration.
          </p>
        </div>
        <Link
          className="text-sm font-medium underline underline-offset-4"
          href="/merchant/webhooks"
        >
          Manage integration
        </Link>
      </div>
      <label className="flex items-center gap-3 text-sm">
        Status
        <select
          className="rounded-md border bg-background px-3 py-2"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All statuses</option>
          {orderStatuses.map((s) => (
            <option key={s} value={s}>
              {statusLabel(s)}
            </option>
          ))}
        </select>
      </label>
      {orders.isPending ? (
        <p role="status">Loading orders...</p>
      ) : orders.isError ? (
        <div role="alert" className="rounded-lg border p-4">
          <p>{orders.error.message}</p>
          <Button
            className="mt-3"
            variant="outline"
            onClick={() => orders.refetch()}
          >
            Try again
          </Button>
        </div>
      ) : orders.data.content.length === 0 ? (
        <div className="rounded-xl border border-dashed p-10 text-center">
          <h3 className="font-medium">
            No orders {status ? "with this status" : "yet"}
          </h3>
          <p className="mt-2 text-sm text-muted-foreground">
            {status
              ? "Choose another status to see your other orders."
              : "Create an API key in Integrations, then place your first order from the Demo Store."}
          </p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto rounded-xl border">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted">
                <tr>
                  {[
                    "Tracking number",
                    "Shop reference",
                    "Status",
                    "Delivery fee",
                    "Created",
                  ].map((h) => (
                    <th
                      key={h}
                      className="whitespace-nowrap px-4 py-3 font-medium"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {orders.data.content.map((order) => (
                  <tr key={order.id} className="border-t">
                    <td className="whitespace-nowrap px-4 py-4">
                      <Link
                        className="font-medium underline underline-offset-4"
                        href={
                          "/merchant/orders/" +
                          encodeURIComponent(order.trackingNo)
                        }
                      >
                        {order.trackingNo}
                      </Link>
                    </td>
                    <td className="px-4 py-4">{order.orderRef}</td>
                    <td className="whitespace-nowrap px-4 py-4">
                      {statusLabel(order.status)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-4 tabular-nums">
                      {money(order.feeLkr)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-4">
                      {new Date(order.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between gap-4 text-sm">
            <span>
              {orders.data.totalElements} orders | Page {page + 1}
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                disabled={(page + 1) * 20 >= orders.data.totalElements}
                onClick={() => setPage(page + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}
    </section>
  );
}
