import { describe, expect, it } from "vitest";
import { DASHBOARD, NAV, rolesFromToken, trackingBackLink } from "./roles";
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

describe("tracking page back link", () => {
  const token = (roles: string[]) => `x.${btoa(JSON.stringify({ roles }))}.y`;

  it("sends a logged-in customer back to their deliveries", () => {
    expect(trackingBackLink(rolesFromToken(token(["CUSTOMER"])))).toEqual({ href: "/customer/deliveries", label: "My deliveries" });
  });

  it("sends a merchant back to their orders", () => {
    expect(trackingBackLink(rolesFromToken(token(["MERCHANT"]))).href).toBe("/merchant");
  });

  it("keeps the public search for visitors without a login or with a broken cookie", () => {
    const search = { href: "/", label: "Track another parcel" };
    expect(trackingBackLink(rolesFromToken(undefined))).toEqual(search);
    expect(trackingBackLink(rolesFromToken("not-a-token"))).toEqual(search);
    expect(trackingBackLink(rolesFromToken(token(["COURIER"])))).toEqual(search);
  });
});
