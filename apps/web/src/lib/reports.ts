import { addDays, daysBetween, isValidDate, todayInColombo } from "./dates";

/** One row of GET /reports/daily. */
export type DailyStats = { day: string; created: number; delivered: number; failed: number };

/** GET /reports/summary. Rates are percentages of completed deliveries, null when none completed. */
export type ReportSummary = {
  from: string;
  to: string;
  created: number;
  delivered: number;
  failed: number;
  completed: number;
  successRate: number | null;
  failureRate: number | null;
};

// Mirrors services/reporting-service/.../ReportService.java.
export const MAX_DAYS = 366;
export const DEFAULT_DAYS = 30;

export type DateRange = { from: string; to: string };

export function defaultRange(today: string = todayInColombo()): DateRange {
  return { from: addDays(today, -(DEFAULT_DAYS - 1)), to: today };
}

/** Why the server would reject this range, or undefined if it is fine. */
export function rangeProblem({ from, to }: DateRange): string | undefined {
  if (!isValidDate(from) || !isValidDate(to)) return "Pick both dates.";
  if (from > to) return "The start date must be on or before the end date.";
  if (daysBetween(from, to) + 1 > MAX_DAYS) return `A report can cover at most ${MAX_DAYS} days.`;
  return undefined;
}

export function rangeQuery({ from, to }: DateRange): string {
  return new URLSearchParams({ from, to }).toString();
}

export function formatRate(rate: number | null | undefined): string {
  return rate === null || rate === undefined ? "–" : `${rate.toFixed(1)}%`;
}

/** A round axis maximum at or above the largest value (1, 2, 5 × 10ⁿ). */
export function niceMax(value: number): number {
  if (value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  for (const step of [1, 2, 5, 10]) {
    if (step * magnitude >= value) return step * magnitude;
  }
  return 10 * magnitude;
}

const DAY_LABEL = new Intl.DateTimeFormat("en-LK", { day: "numeric", month: "short", timeZone: "UTC" });

/** Short day label ("Nov 14" in en-LK) for a yyyy-mm-dd day. */
export function dayLabel(day: string): string {
  return DAY_LABEL.format(new Date(`${day}T00:00:00Z`));
}
