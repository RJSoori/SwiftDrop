import { cookies } from "next/headers";
import { rolesFromToken, trackingBackLink } from "@/lib/roles";
import { TrackingPage } from "./tracking-page";

export default async function TrackParcelPage({ params }: { params: Promise<{ trackingNo: string }> }) {
  const { trackingNo } = await params;
  // A logged-in customer or merchant goes back to their own list; anyone else (the QR code on a label) to the search.
  const back = trackingBackLink(rolesFromToken((await cookies()).get("sd_access")?.value));
  return <TrackingPage trackingNo={trackingNo} back={back} />;
}
