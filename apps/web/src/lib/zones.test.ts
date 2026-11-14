import { describe, expect, it } from "vitest";
import { addDays, todayInColombo } from "./dates";
import {
  feeFor,
  outlineProblem,
  pointsFromPolygon,
  polygonFromPoints,
  pricingRuleSchema,
  toRuleRequest,
  zoneNameSchema,
  type LatLng,
} from "./zones";

const SQUARE: LatLng[] = [[6.1, 79.1], [6.1, 79.3], [6.3, 79.3], [6.3, 79.1]];

describe("polygonFromPoints / pointsFromPolygon", () => {
  it("swaps to GeoJSON order, closes the ring and rounds to 6 decimals", () => {
    const polygon = polygonFromPoints([[6.1234567, 79.1], [6.1, 79.3], [6.3, 79.3]]);
    expect(polygon.type).toBe("Polygon");
    expect(polygon.coordinates[0]).toEqual([[79.1, 6.123457], [79.3, 6.1], [79.3, 6.3], [79.1, 6.123457]]);
  });

  it("round-trips without the closing point", () => {
    expect(pointsFromPolygon(polygonFromPoints(SQUARE))).toEqual(SQUARE);
    expect(pointsFromPolygon(null)).toEqual([]);
  });
});

describe("outlineProblem", () => {
  it("accepts a simple shape inside Sri Lanka", () => {
    expect(outlineProblem(SQUARE)).toBeUndefined();
  });

  it("needs three distinct points", () => {
    expect(outlineProblem([[6.1, 79.1], [6.1, 79.1], [6.2, 79.2]])).toMatch(/three points/);
  });

  it("rejects points outside Sri Lanka", () => {
    expect(outlineProblem([[4.0, 73.0], [4.0, 73.5], [4.5, 73.5]])).toMatch(/inside Sri Lanka/);
  });

  it("rejects an outline that crosses itself", () => {
    const bowTie: LatLng[] = [[6.1, 79.1], [6.3, 79.3], [6.1, 79.3], [6.3, 79.1]];
    expect(outlineProblem(bowTie)).toMatch(/crosses itself/);
  });
});

describe("feeFor", () => {
  const rule = { baseFee: 450, perKgFee: 100, sizeMultiplier: 1.25 };

  it("matches parcel-service for each strategy", () => {
    expect(feeFor({ ...rule, strategy: "WEIGHT_TIERED" }, 2)).toBe(650);
    expect(feeFor({ ...rule, strategy: "FLAT_RATE" }, 2)).toBe(450);
    expect(feeFor({ ...rule, strategy: "EXPRESS" }, 2)).toBe(750);
  });

  it("applies the size multiplier to large parcels only", () => {
    expect(feeFor({ ...rule, strategy: "WEIGHT_TIERED" }, 2, "LARGE")).toBe(812.5);
    expect(feeFor({ ...rule, strategy: "WEIGHT_TIERED" }, 2, "SMALL")).toBe(650);
  });
});

describe("zone and rule validation", () => {
  const valid = { strategy: "WEIGHT_TIERED", baseFee: 450, perKgFee: 100, sizeMultiplier: 1.25, validFrom: "" };

  it("accepts a valid rule and leaves validFrom out when empty", () => {
    const parsed = pricingRuleSchema.parse(valid);
    expect(toRuleRequest(parsed)).toEqual({ strategy: "WEIGHT_TIERED", baseFee: 450, perKgFee: 100, sizeMultiplier: 1.25, validFrom: undefined });
  });

  it("rejects negative fees, extra decimals and silly multipliers", () => {
    expect(pricingRuleSchema.safeParse({ ...valid, baseFee: -1 }).success).toBe(false);
    expect(pricingRuleSchema.safeParse({ ...valid, perKgFee: 10.123 }).success).toBe(false);
    expect(pricingRuleSchema.safeParse({ ...valid, sizeMultiplier: 9 }).success).toBe(false);
    expect(pricingRuleSchema.safeParse({ ...valid, baseFee: Number.NaN }).success).toBe(false);
    expect(pricingRuleSchema.safeParse({ ...valid, strategy: "DISTANCE_BASED" }).success).toBe(false);
  });

  it("allows today or later as a start date, not the past", () => {
    const today = todayInColombo();
    expect(pricingRuleSchema.safeParse({ ...valid, validFrom: today }).success).toBe(true);
    expect(pricingRuleSchema.safeParse({ ...valid, validFrom: addDays(today, 7) }).success).toBe(true);
    expect(pricingRuleSchema.safeParse({ ...valid, validFrom: addDays(today, -1) }).success).toBe(false);
  });

  it("checks zone names like the server", () => {
    expect(zoneNameSchema.safeParse("Nuwara Eliya").success).toBe(true);
    expect(zoneNameSchema.safeParse("K").success).toBe(false);
    expect(zoneNameSchema.safeParse("<script>").success).toBe(false);
  });
});
