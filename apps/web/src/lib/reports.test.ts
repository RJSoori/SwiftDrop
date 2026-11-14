import { describe, expect, it } from "vitest";
import { addDays, daysBetween, isValidDate, todayInColombo } from "./dates";
import { dayLabel, defaultRange, formatRate, niceMax, rangeProblem, rangeQuery } from "./reports";

describe("dates", () => {
  it("uses Sri Lankan time for today", () => {
    // 20:00 UTC on the 13th is already the 14th in Colombo (UTC+5:30)
    expect(todayInColombo(new Date("2026-11-13T20:00:00Z"))).toBe("2026-11-14");
    expect(todayInColombo(new Date("2026-11-13T18:00:00Z"))).toBe("2026-11-13");
  });

  it("adds days across month ends and counts them", () => {
    expect(addDays("2026-11-30", 1)).toBe("2026-12-01");
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
    expect(daysBetween("2026-11-01", "2026-11-30")).toBe(29);
  });

  it("validates real calendar dates", () => {
    expect(isValidDate("2026-02-28")).toBe(true);
    expect(isValidDate("2026-02-30")).toBe(false);
    expect(isValidDate("14-11-2026")).toBe(false);
  });
});

describe("report ranges", () => {
  it("defaults to the last 30 days ending today", () => {
    expect(defaultRange("2026-11-14")).toEqual({ from: "2026-10-16", to: "2026-11-14" });
  });

  it("matches the server's rules", () => {
    expect(rangeProblem({ from: "2026-11-01", to: "2026-11-14" })).toBeUndefined();
    expect(rangeProblem({ from: "2026-11-15", to: "2026-11-14" })).toMatch(/on or before/);
    expect(rangeProblem({ from: "2025-11-01", to: "2026-11-14" })).toMatch(/366 days/);
    expect(rangeProblem({ from: "", to: "2026-11-14" })).toMatch(/Pick both/);
  });

  it("builds the query string", () => {
    expect(rangeQuery({ from: "2026-11-01", to: "2026-11-14" })).toBe("from=2026-11-01&to=2026-11-14");
  });
});

describe("formatting", () => {
  it("shows rates with one decimal and a dash when there is none", () => {
    expect(formatRate(66.7)).toBe("66.7%");
    expect(formatRate(100)).toBe("100.0%");
    expect(formatRate(null)).toBe("–");
  });

  it("rounds the axis up to 1, 2 or 5 times a power of ten", () => {
    expect(niceMax(0)).toBe(1);
    expect(niceMax(3)).toBe(5);
    expect(niceMax(7)).toBe(10);
    expect(niceMax(12)).toBe(20);
    expect(niceMax(50)).toBe(50);
  });

  it("labels days without shifting them by time zone", () => {
    const label = dayLabel("2026-11-14");
    expect(label).toMatch(/\b14\b/);
    expect(label).toMatch(/Nov/);
  });
});
