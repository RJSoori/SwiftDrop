import { z } from "zod";
import { isValidDate, todayInColombo } from "./dates";

/** [longitude, latitude], the GeoJSON order. */
export type Position = [number, number];
/** [latitude, longitude], the Leaflet order. */
export type LatLng = [number, number];

export type GeoJsonPolygon = { type: "Polygon"; coordinates: Position[][] };

export type PricingStrategy = "FLAT_RATE" | "WEIGHT_TIERED" | "EXPRESS";

export type PricingRule = {
  id: string;
  zoneId: string;
  strategy: PricingStrategy;
  baseFee: number;
  perKgFee: number;
  sizeMultiplier: number;
  validFrom: string;
  active: boolean;
  current: boolean;
  createdAt: string;
};

/** GET /zones: what any logged-in user sees. */
export type ZoneSummary = { id: string; name: string; boundary: GeoJsonPolygon };

/** GET /admin/zones. */
export type Zone = ZoneSummary & { active: boolean; createdAt: string; currentRule: PricingRule | null };

export const STRATEGIES: PricingStrategy[] = ["WEIGHT_TIERED", "FLAT_RATE", "EXPRESS"];

export const STRATEGY_LABEL: Record<PricingStrategy, string> = {
  WEIGHT_TIERED: "Weight-tiered",
  FLAT_RATE: "Flat rate",
  EXPRESS: "Express",
};

export const STRATEGY_HELP: Record<PricingStrategy, string> = {
  WEIGHT_TIERED: "Base fee plus the per-kg fee for each chargeable kg.",
  FLAT_RATE: "The base fee for every parcel, whatever it weighs.",
  EXPRESS: "Base fee plus 1.5 × the per-kg fee for each chargeable kg.",
};

// Mirrors services/parcel-service/.../zone/GeoJsonPolygons.java and ZoneDtos.java. Change both together.
export const SRI_LANKA_BOUNDS = { minLng: 79.0, maxLng: 82.5, minLat: 5.5, maxLat: 10.5 } as const;
export const MAX_POINTS = 500;
const ZONE_NAME = /^[\p{L}0-9 .'()&-]+$/u;

export const zoneNameSchema = z
  .string()
  .trim()
  .min(2, "Use at least 2 characters")
  .max(100, "Keep it under 100 characters")
  .regex(ZONE_NAME, "Use letters, digits, spaces and . ' ( ) & -");

const twoDecimals = (n: number) => Math.abs(Math.round(n * 100) - n * 100) < 1e-6;

const money = (max: number) =>
  z
    .number({ error: "Enter an amount" })
    .min(0, "Cannot be negative")
    .max(max, `At most ${max.toLocaleString("en-LK")}`)
    .refine(twoDecimals, "Use at most 2 decimals");

export const pricingRuleSchema = z.object({
  strategy: z.enum(STRATEGIES, "Choose a pricing strategy"),
  baseFee: money(100_000),
  perKgFee: money(10_000),
  sizeMultiplier: z
    .number({ error: "Enter a multiplier" })
    .min(0.5, "At least 0.5")
    .max(5, "At most 5")
    .refine(twoDecimals, "Use at most 2 decimals"),
  validFrom: z
    .string()
    .refine((d) => d === "" || isValidDate(d), "Pick a date")
    .refine((d) => d === "" || d >= todayInColombo(), "A rule cannot start in the past"),
});

export type PricingRuleInput = z.infer<typeof pricingRuleSchema>;

/** Body for POST /admin/zones/{id}/pricing-rules; an empty start date means today. */
export function toRuleRequest(input: PricingRuleInput) {
  return {
    strategy: input.strategy,
    baseFee: input.baseFee,
    perKgFee: input.perKgFee,
    sizeMultiplier: input.sizeMultiplier,
    validFrom: input.validFrom === "" ? undefined : input.validFrom,
  };
}

/** A closed GeoJSON polygon from map points (Leaflet order), coordinates rounded to 6 decimals. */
export function polygonFromPoints(points: LatLng[]): GeoJsonPolygon {
  const ring: Position[] = points.map(([lat, lng]) => [round6(lng), round6(lat)]);
  if (ring.length > 0) ring.push([...ring[0]] as Position);
  return { type: "Polygon", coordinates: [ring] };
}

/** Map points (Leaflet order) from a polygon, without the closing point. */
export function pointsFromPolygon(polygon: GeoJsonPolygon | null | undefined): LatLng[] {
  const ring = polygon?.coordinates?.[0] ?? [];
  const points = ring.map(([lng, lat]) => [lat, lng] as LatLng);
  if (points.length > 1 && samePoint(points[0], points[points.length - 1])) points.pop();
  return points;
}

/** What is wrong with a drawn outline, or undefined when the server will accept its shape. */
export function outlineProblem(points: LatLng[]): string | undefined {
  const distinct = new Set(points.map(([lat, lng]) => `${round6(lat)},${round6(lng)}`));
  if (distinct.size < 3) return "Click at least three points on the map.";
  if (points.length > MAX_POINTS) return `Use at most ${MAX_POINTS} points.`;
  const { minLat, maxLat, minLng, maxLng } = SRI_LANKA_BOUNDS;
  if (points.some(([lat, lng]) => lat < minLat || lat > maxLat || lng < minLng || lng > maxLng)) {
    return "Keep every point inside Sri Lanka.";
  }
  if (selfIntersects(points)) return "The outline crosses itself. Undo the last points and redraw.";
  return undefined;
}

/** Fee for one parcel under a rule, the same formula parcel-service uses (chargeable weight given). */
export function feeFor(
  rule: Pick<PricingRule, "strategy" | "baseFee" | "perKgFee" | "sizeMultiplier">,
  chargeableKg: number,
  size: "SMALL" | "MEDIUM" | "LARGE" = "MEDIUM",
): number {
  const perKg = rule.strategy === "FLAT_RATE" ? 0 : rule.perKgFee * chargeableKg * (rule.strategy === "EXPRESS" ? 1.5 : 1);
  let fee = round2(rule.baseFee + perKg);
  if (size === "LARGE") fee = round2(fee * rule.sizeMultiplier);
  return fee;
}

const LKR = new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR", minimumFractionDigits: 2 });

export function formatLkr(amount: number): string {
  return LKR.format(amount);
}

// ---------- geometry ----------

function selfIntersects(points: LatLng[]): boolean {
  const n = points.length;
  if (n < 4) return false;
  for (let i = 0; i < n; i++) {
    const a1 = points[i];
    const a2 = points[(i + 1) % n];
    for (let j = i + 1; j < n; j++) {
      // adjacent edges share a vertex; that is not a crossing
      if ((j + 1) % n === i || (i + 1) % n === j) continue;
      if (segmentsCross(a1, a2, points[j], points[(j + 1) % n])) return true;
    }
  }
  return false;
}

function segmentsCross(p1: LatLng, p2: LatLng, p3: LatLng, p4: LatLng): boolean {
  const d1 = orientation(p3, p4, p1);
  const d2 = orientation(p3, p4, p2);
  const d3 = orientation(p1, p2, p3);
  const d4 = orientation(p1, p2, p4);
  return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
}

function orientation(a: LatLng, b: LatLng, c: LatLng): number {
  return (b[1] - a[1]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[1] - a[1]);
}

function samePoint(a: LatLng, b: LatLng) {
  return round6(a[0]) === round6(b[0]) && round6(a[1]) === round6(b[1]);
}

function round6(n: number) {
  return Math.round(n * 1e6) / 1e6;
}

function round2(n: number) {
  return Math.round(n * 100) / 100;
}
