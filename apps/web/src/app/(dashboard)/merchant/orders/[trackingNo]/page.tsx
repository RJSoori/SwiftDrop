"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { money, statusLabel, type OrderDetail } from "@/lib/merchant";
import { PageHeading } from "@/components/dashboard";

export default function MerchantOrderDetail() {
  const { trackingNo } = useParams<{ trackingNo: string }>();
  const order = useQuery({
    queryKey: ["merchant-order", trackingNo],
    queryFn: () =>
      api<OrderDetail>("/merchant/orders/" + encodeURIComponent(trackingNo)),
    refetchInterval: 10000,
    retry: 1,
  });
  return (
    <div className="max-w-3xl">
      <Link className="text-sm underline underline-offset-4" href="/merchant">
        Back to orders
      </Link>
      <div className="mt-5">
        <PageHeading
          title={trackingNo}
          intro="Delivery status and history for your shop order."
        />
      </div>
      {order.isPending ? (
        <p className="mt-8" role="status">
          Loading order...
        </p>
      ) : order.isError ? (
        <p className="mt-8 text-destructive" role="alert">
          {order.error.message}
        </p>
      ) : (
        <>
          <div className="mt-8 grid gap-4 rounded-xl border bg-card p-6 sm:grid-cols-3">
            <div>
              <p className="text-sm text-muted-foreground">Shop reference</p>
              <p className="mt-2 font-medium break-all">
                {order.data.order.orderRef}
              </p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">Status</p>
              <p className="mt-2 font-medium">
                {statusLabel(order.data.order.status)}
              </p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">Delivery fee</p>
              <p className="mt-2 font-medium">
                {money(order.data.order.feeLkr)}
              </p>
            </div>
          </div>
          <section className="mt-8">
            <h2 className="text-xl font-semibold">Status history</h2>
            <ol className="mt-5 space-y-0 border-l-2 pl-6">
              {order.data.history.map((entry, index) => (
                <li key={entry.occurredAt + index} className="relative pb-7">
                  <span
                    aria-hidden
                    className="absolute -left-[31px] top-1 size-3 rounded-full bg-brand"
                  />
                  <p className="font-medium">{statusLabel(entry.toStatus)}</p>
                  <time
                    className="mt-1 block text-sm text-muted-foreground"
                    dateTime={entry.occurredAt}
                  >
                    {new Date(entry.occurredAt).toLocaleString()}
                  </time>
                </li>
              ))}
            </ol>
          </section>
          <Link
            className="text-sm underline underline-offset-4"
            href={"/track/" + encodeURIComponent(trackingNo)}
          >
            Open public tracking
          </Link>
        </>
      )}
    </div>
  );
}
