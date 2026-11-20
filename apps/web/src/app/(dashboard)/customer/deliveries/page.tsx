import { MyDeliveries } from "@/components/customer-deliveries";
import { PageHeading } from "@/components/dashboard";

export default function CustomerDeliveriesPage() {
  return (
    <div className="space-y-8">
      <PageHeading
        title="My deliveries"
        intro="Every parcel you have booked, with its live status, tracking page, receipt and label."
      />
      <MyDeliveries />
    </div>
  );
}
