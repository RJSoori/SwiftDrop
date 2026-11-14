"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { PageHeading, ServiceAccess } from "@/components/dashboard";
import { buttonVariants } from "@/components/ui/button";
import { api, type MerchantApplication } from "@/lib/api";
import { todayInColombo } from "@/lib/dates";
import { formatRate, type ReportSummary } from "@/lib/reports";

export default function AdminDashboard() {
  const pending = useQuery({
    queryKey: ["merchants", "PENDING"],
    queryFn: () => api<MerchantApplication[]>("/auth/admin/merchants?status=PENDING"),
  });
  const count = pending.data?.length;
  const today = todayInColombo();
  const todayStats = useQuery({
    queryKey: ["reports", "summary", "today", today],
    queryFn: () => api<ReportSummary>(`/reports/summary?from=${today}&to=${today}`),
  });

  return (
    <>
      <PageHeading title="Admin" intro="Approve merchants, manage staff accounts, zones and pricing, and follow today's deliveries." />
      <section aria-labelledby="today" className="mt-8 max-w-4xl">
        <div className="flex flex-wrap items-baseline justify-between gap-3">
          <h2 id="today" className="text-base font-semibold">Today</h2>
          <Link href="/admin/reports" className="text-sm font-medium text-brand hover:underline">All reports</Link>
        </div>
        <dl className="mt-3 grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[
            ["Parcels booked", todayStats.data?.created],
            ["Delivered", todayStats.data?.delivered],
            ["Failed", todayStats.data?.failed],
            ["Success rate", todayStats.data ? formatRate(todayStats.data.successRate) : undefined],
          ].map(([label, value]) => (
            <div key={label} className="rounded-xl border bg-card p-4">
              <dt className="text-sm text-muted-foreground">{label}</dt>
              <dd className="tabular mt-1 text-2xl font-semibold">
                {value ?? "–"}
              </dd>
            </div>
          ))}
        </dl>
        {todayStats.isError && <p className="mt-2 text-sm text-muted-foreground">Today&apos;s numbers are unavailable right now.</p>}
      </section>
      <div className="mt-8 flex max-w-2xl flex-wrap items-center justify-between gap-4 rounded-xl border bg-card p-5">
        <p className="text-lg">
          {count === undefined
            ? "Checking merchant applications…"
            : count === 0
              ? "No merchant applications are waiting."
              : `${count} merchant ${count === 1 ? "application is" : "applications are"} waiting for review.`}
        </p>
        <Link href="/admin/merchants" className={buttonVariants({ size: "lg" })}>
          Review applications
        </Link>
      </div>
      <ServiceAccess />
    </>
  );
}
