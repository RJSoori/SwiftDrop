import { describe, expect, it } from "vitest";
import {
  deliveryDetailsSchema,
  deliveryOtpSchema,
  failDeliverySchema,
  nextCourierStatuses,
  proofImageSchema,
  proofUploadResponseSchema,
  statusUpdateSchema,
  todaysDeliveriesSchema,
} from "./courier-deliveries";

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

  it("accepts CREATED parcels", () => {
    expect(todaysDeliveriesSchema.parse([{ ...validDelivery, status: "CREATED" }])[0]?.status).toBe("CREATED");
  });
});

describe("courier delivery details response schema", () => {
  const validDetails = {
    parcelId: "7b2f410c-68e9-4c65-99ba-08a60b7678f7",
    status: "OUT_FOR_DELIVERY",
    recipientName: "Nimal Perera",
    recipientPhone: "Recipient phone",
  };

  it("accepts parcel detail responses", () => {
    expect(deliveryDetailsSchema.parse(validDetails)).toEqual(validDetails);
  });

  it("rejects malformed or incomplete parcel detail responses", () => {
    expect(deliveryDetailsSchema.safeParse({ ...validDetails, parcelId: "not-a-uuid" }).success).toBe(false);
    expect(deliveryDetailsSchema.safeParse({ ...validDetails, status: "PENDING" }).success).toBe(false);
    expect(deliveryDetailsSchema.safeParse({ ...validDetails, recipientName: "" }).success).toBe(false);
    expect(deliveryDetailsSchema.safeParse({ ...validDetails, recipientPhone: null }).success).toBe(false);
  });
});

describe("courier status updates", () => {
  it.each([
    ["CREATED", ["PICKED_UP"]],
    ["PICKED_UP", ["IN_TRANSIT"]],
    ["IN_TRANSIT", ["OUT_FOR_DELIVERY"]],
    ["FAILED", ["OUT_FOR_DELIVERY"]],
    ["OUT_FOR_DELIVERY", []],
    ["DELIVERED", []],
  ] as const)("offers only valid next statuses from %s", (status, expected) => {
    expect(nextCourierStatuses(status)).toEqual(expected);
  });

  it("validates the target status and trims optional notes", () => {
    expect(statusUpdateSchema.parse({ toStatus: "IN_TRANSIT", note: "  On the way  " })).toEqual({
      toStatus: "IN_TRANSIT",
      note: "On the way",
    });
    expect(statusUpdateSchema.safeParse({ toStatus: "DELIVERED", note: "" }).success).toBe(false);
    expect(statusUpdateSchema.safeParse({ toStatus: "PICKED_UP", note: "x".repeat(501) }).success).toBe(false);
  });
});

describe("courier delivery OTP validation", () => {
  it("accepts a four-digit OTP and a trimmed recipient name", () => {
    expect(deliveryOtpSchema.parse({ otp: "0123", recipientName: "  Nimal Perera  " })).toEqual({
      otp: "0123",
      recipientName: "Nimal Perera",
    });
  });

  it("requires exactly four digits and a non-empty recipient name", () => {
    for (const otp of ["123", "12345", "12a4", "１２３４"]) {
      expect(deliveryOtpSchema.safeParse({ otp, recipientName: "Nimal Perera" }).success).toBe(false);
    }
    expect(deliveryOtpSchema.safeParse({ otp: "1234", recipientName: "   " }).success).toBe(false);
  });
});

describe("courier delivery failure validation", () => {
  it("accepts each failure reason and trims an optional note", () => {
    for (const reasonCode of ["RECIPIENT_UNAVAILABLE", "WRONG_ADDRESS", "REFUSED"] as const) {
      expect(failDeliverySchema.parse({ reasonCode, note: "  " })).toEqual({ reasonCode, note: "" });
    }
    expect(failDeliverySchema.parse({ reasonCode: "OTHER", note: "  Gate was locked  " })).toEqual({
      reasonCode: "OTHER",
      note: "Gate was locked",
    });
  });

  it("requires a note only for Other and limits notes to 500 characters", () => {
    expect(failDeliverySchema.safeParse({ reasonCode: "OTHER", note: "   " }).success).toBe(false);
    expect(failDeliverySchema.safeParse({ reasonCode: "WRONG_ADDRESS", note: "" }).success).toBe(true);
    expect(failDeliverySchema.safeParse({ reasonCode: "REFUSED", note: "x".repeat(501) }).success).toBe(false);
    expect(failDeliverySchema.safeParse({ reasonCode: "UNKNOWN", note: "" }).success).toBe(false);
  });
});

describe("courier photo proof validation", () => {
  it("accepts JPEG and PNG files up to five megabytes", () => {
    const jpeg = new File(["photo"], "proof.jpg", { type: "image/jpeg" });
    const png = new File(["photo"], "proof.png", { type: "image/png" });

    expect(proofImageSchema.safeParse(jpeg).success).toBe(true);
    expect(proofImageSchema.safeParse(png).success).toBe(true);
    expect(proofImageSchema.safeParse(new File(["x".repeat(5 * 1024 * 1024)], "large.png", { type: "image/png" })).success)
      .toBe(true);
  });

  it("rejects unsupported types and files larger than five megabytes", () => {
    const unsupported = new File(["photo"], "proof.gif", { type: "image/gif" });
    const oversized = new File(["x".repeat(5 * 1024 * 1024 + 1)], "large.jpg", { type: "image/jpeg" });

    expect(proofImageSchema.safeParse(unsupported).success).toBe(false);
    expect(proofImageSchema.safeParse(oversized).success).toBe(false);
  });

  it("validates the proof identifier returned by the upload endpoint", () => {
    expect(proofUploadResponseSchema.safeParse({ proofId: "7b2f410c-68e9-4c65-99ba-08a60b7678f7" }).success).toBe(true);
    expect(proofUploadResponseSchema.safeParse({ proofId: "invalid" }).success).toBe(false);
  });
});
