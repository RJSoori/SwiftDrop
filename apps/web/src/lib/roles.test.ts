import { describe, expect, it } from "vitest";
import { DASHBOARD, NAV } from "./roles";
import type { Role } from "./api";

describe("dashboard navigation", () => {
  it("keeps every link inside its own role's dashboard", () => {
    for (const role of Object.keys(NAV) as Role[]) {
      for (const item of NAV[role]) {
        expect(item.href === DASHBOARD[role] || item.href.startsWith(DASHBOARD[role] + "/"), `${role} ${item.href}`).toBe(true);
      }
    }
  });

  it("gives customers a booking page and a deliveries page", () => {
    expect(NAV.CUSTOMER).toEqual([
      { href: "/customer", label: "Book a delivery" },
      { href: "/customer/deliveries", label: "My deliveries" },
    ]);
  });

  it("links every admin page, including the audit log", () => {
    expect(NAV.ADMIN.map((item) => item.href)).toEqual([
      "/admin",
      "/admin/merchants",
      "/admin/users",
      "/admin/zones",
      "/admin/reports",
      "/admin/audit-log",
    ]);
  });
});
