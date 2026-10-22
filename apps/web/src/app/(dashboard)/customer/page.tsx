"use client";

import { PageHeading, ServiceAccess } from "@/components/dashboard";
import { useMe } from "@/lib/session";

export default function CustomerDashboard() {
  const me = useMe();
  return (
    <>
      <PageHeading
        title={`Hello, ${me.data?.name.split(" ")[0] ?? ""}`}
        intro="Parcels you send or expect will be listed here, with live status from pickup to delivery."
      />
      <ServiceAccess />
    </>
  );
}
