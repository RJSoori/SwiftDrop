"use client";

import { PageHeading } from "@/components/dashboard";
import { ReportsView } from "@/components/reports/reports-view";

export default function DispatcherReportsPage() {
  return (
    <>
      <PageHeading
        title="Reports"
        intro="Parcels booked, delivered and failed per day, to spot busy days and failed-delivery trends."
      />
      <ReportsView />
    </>
  );
}
