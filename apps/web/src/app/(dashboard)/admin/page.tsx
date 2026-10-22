"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { PageHeading, ServiceAccess } from "@/components/dashboard";
import { buttonVariants } from "@/components/ui/button";
import { api, type MerchantApplication } from "@/lib/api";

export default function AdminDashboard() {
  const pending = useQuery({
    queryKey: ["merchants", "PENDING"],
    queryFn: () => api<MerchantApplication[]>("/auth/admin/merchants?status=PENDING"),
  });
  const count = pending.data?.length;

  return (
    <>
      <PageHeading title="Admin" intro="Approve merchants and manage staff accounts." />
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
