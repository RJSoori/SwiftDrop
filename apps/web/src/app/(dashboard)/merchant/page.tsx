"use client";

import { PageHeading, ServiceAccess } from "@/components/dashboard";
import { useLogout, useMe } from "@/lib/session";
import { Button } from "@/components/ui/button";

export default function MerchantDashboard() {
  const me = useMe();
  const logout = useLogout();
  const status = me.data?.merchantStatus;

  if (status === "PENDING" || status === "REJECTED" || status === "SUSPENDED") {
    const copy = {
      PENDING: {
        title: "Your application is being reviewed",
        body: "A SwiftDrop admin checks every new merchant's business details. Once your account is approved, log out and log back in to start booking pickups.",
      },
      REJECTED: {
        title: "Your application was not approved",
        body: "We could not verify your business details. Contact SwiftDrop support with your business registration certificate to apply again.",
      },
      SUSPENDED: {
        title: "Your merchant account is suspended",
        body: "Contact SwiftDrop support to restore access.",
      },
    }[status];
    return (
      <div className="max-w-2xl rounded-xl border-2 border-dashed border-tape bg-card p-6 sm:p-8">
        <PageHeading title={copy.title} intro={copy.body} />
        <Button variant="outline" size="lg" className="mt-6" onClick={logout}>
          Log out
        </Button>
      </div>
    );
  }

  return (
    <>
      <PageHeading
        title={`Hello, ${me.data?.name.split(" ")[0] ?? ""}`}
        intro="Book pickups for your orders and follow every delivery from here."
      />
      <ServiceAccess />
    </>
  );
}
