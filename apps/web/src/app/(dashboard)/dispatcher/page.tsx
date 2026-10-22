"use client";

import { PageHeading, ServiceAccess } from "@/components/dashboard";
import { useMe } from "@/lib/session";

export default function DispatcherDashboard() {
  const me = useMe();
  return (
    <>
      <PageHeading
        title={`Hello, ${me.data?.name.split(" ")[0] ?? ""}`}
        intro="Unassigned parcels and courier routes will be managed from here."
      />
      <ServiceAccess />
    </>
  );
}
