"use client";

import { PageHeading } from "@/components/dashboard";
import { ReportsView } from "@/components/reports/reports-view";

export default function AdminReportsPage() {
  return (
    <>
      <PageHeading
        title="Reports"
        intro="Parcels booked, delivered and failed per day. The success rate counts deliveries that reached Delivered out of all that finished as Delivered or Failed."
      />
      <ReportsView />
    </>
  );
}
