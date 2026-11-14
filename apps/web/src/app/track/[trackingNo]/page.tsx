import { TrackingPage } from "./tracking-page";

export default async function TrackParcelPage({ params }: { params: Promise<{ trackingNo: string }> }) {
  const { trackingNo } = await params;
  return <TrackingPage trackingNo={trackingNo} />;
}
