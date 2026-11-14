export type MerchantOrder = {
  id: string;
  parcelId: string;
  trackingNo: string;
  orderRef: string;
  status: string;
  feeLkr: number;
  createdAt: string;
  updatedAt: string;
};
export type OrderPage = {
  content: MerchantOrder[];
  totalElements: number;
  page: number;
  size: number;
};
export type OrderDetail = {
  order: MerchantOrder;
  history: {
    fromStatus: string | null;
    toStatus: string;
    occurredAt: string;
  }[];
};
export type WebhookEndpoint = {
  url: string | null;
  active: boolean;
  updatedAt: string | null;
};
export type WebhookDelivery = {
  id: string;
  eventId: string;
  attempt: number;
  state: string;
  statusCode: number | null;
  lastError: string | null;
  nextRetryAt: string;
  createdAt: string;
  deliveredAt: string | null;
};
export type ApiKey = {
  id: string;
  prefix: string;
  createdAt: string;
  revokedAt: string | null;
};
export const orderStatuses = [
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "FAILED",
  "CANCELLED",
];
export function statusLabel(status: string) {
  return status
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (c) => c.toUpperCase());
}
export function money(amount: number) {
  return new Intl.NumberFormat("en-LK", {
    style: "currency",
    currency: "LKR",
  }).format(amount);
}
