import { describe, expect, it } from "vitest";
import { deliveryDetailsSchema, todaysDeliveriesSchema } from "./courier-deliveries";

const validDelivery = {
  parcelId: "7b2f410c-68e9-4c65-99ba-08a60b7678f7",
  status: "IN_TRANSIT",
  completed: false,
};

describe("today's courier deliveries response schema", () => {
  it("accepts the delivery summary returned by the API", () => {
    expect(todaysDeliveriesSchema.parse([validDelivery])).toEqual([validDelivery]);
  });

  it("accepts an empty delivery list", () => {
    expect(todaysDeliveriesSchema.parse([])).toEqual([]);
  });

  it("rejects malformed parcel IDs, statuses, and completion flags", () => {
    expect(todaysDeliveriesSchema.safeParse([{ ...validDelivery, parcelId: "not-a-uuid" }]).success).toBe(false);
    expect(todaysDeliveriesSchema.safeParse([{ ...validDelivery, status: "PENDING" }]).success).toBe(false);
    expect(todaysDeliveriesSchema.safeParse([{ ...validDelivery, completed: "false" }]).success).toBe(false);
  });
});

describe("courier delivery details response schema", () => {
  const validDetails = {
    parcelId: "7b2f410c-68e9-4c65-99ba-08a60b7678f7",
    status: "OUT_FOR_DELIVERY",
    recipientName: "Nimal Perera",
    dropOffAddress: "100 University Avenue",
  };

  it("accepts parcel detail responses", () => {
    expect(deliveryDetailsSchema.parse(validDetails)).toEqual(validDetails);
  });

  it("rejects malformed or incomplete parcel detail responses", () => {
    expect(deliveryDetailsSchema.safeParse({ ...validDetails, parcelId: "not-a-uuid" }).success).toBe(false);
    expect(deliveryDetailsSchema.safeParse({ ...validDetails, status: "PENDING" }).success).toBe(false);
    expect(deliveryDetailsSchema.safeParse({ ...validDetails, recipientName: "" }).success).toBe(false);
    expect(deliveryDetailsSchema.safeParse({ ...validDetails, dropOffAddress: null }).success).toBe(false);
  });
});
