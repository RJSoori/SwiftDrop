"use client";

import { PageHeading, ServiceAccess } from "@/components/dashboard";
import { useMe } from "@/lib/session";

export default function CourierDashboard() {
  const me = useMe();
  return (
    <>
      <PageHeading
        title={`Hello, ${me.data?.name.split(" ")[0] ?? ""}`}
        intro="Your pickups and deliveries for the day will appear here once dispatch assigns them."
      />
      <ServiceAccess />
    </>
  );
}
