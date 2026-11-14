/** Calendar dates as yyyy-mm-dd strings in Sri Lankan time, the way the zone and report APIs expect them. */

const COLOMBO_DATE = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Colombo",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

export const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Today's date in Sri Lanka, whatever the browser's time zone. */
export function todayInColombo(now: Date = new Date()): string {
  return COLOMBO_DATE.format(now); // en-CA formats as yyyy-mm-dd
}

export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Whole days from a to b (b - a). */
export function daysBetween(a: string, b: string): number {
  return Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86_400_000);
}

export function isValidDate(date: string): boolean {
  return ISO_DATE.test(date) && !Number.isNaN(Date.parse(`${date}T00:00:00Z`))
    && new Date(`${date}T00:00:00Z`).toISOString().slice(0, 10) === date;
}
