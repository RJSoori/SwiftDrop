import type { Role } from "./api";

/** Each role's dashboard. Shared by middleware.ts (routing) and the dashboard sidebar. */
export const DASHBOARD: Record<Role, string> = {
  ADMIN: "/admin",
  DISPATCHER: "/dispatcher",
  COURIER: "/courier",
  MERCHANT: "/merchant",
  CUSTOMER: "/customer",
};

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: "Admin",
  DISPATCHER: "Dispatcher",
  COURIER: "Courier",
  MERCHANT: "Merchant",
  CUSTOMER: "Customer",
};

const PRIORITY: Role[] = ["ADMIN", "DISPATCHER", "COURIER", "MERCHANT", "CUSTOMER"];

export function primaryRole(roles: readonly string[]): Role | undefined {
  return PRIORITY.find((r) => roles.includes(r));
}

/** Where a signed-in user lands. */
export function homeFor(roles: readonly string[]): string {
  const role = primaryRole(roles);
  return role ? DASHBOARD[role] : "/login";
}

/** The role a dashboard path belongs to, or undefined for public pages. */
export function roleForPath(pathname: string): Role | undefined {
  return PRIORITY.find((r) => pathname === DASHBOARD[r] || pathname.startsWith(DASHBOARD[r] + "/"));
}

export const NAV: Record<Role, { href: string; label: string }[]> = {
  CUSTOMER: [{ href: "/customer", label: "Overview" }],
  MERCHANT: [{ href: "/merchant", label: "Overview" }],
  COURIER: [{ href: "/courier", label: "Overview" }],
  DISPATCHER: [{ href: "/dispatcher", label: "Overview" }],
  ADMIN: [
    { href: "/admin", label: "Overview" },
    { href: "/admin/merchants", label: "Merchant applications" },
    { href: "/admin/users", label: "Staff accounts" },
  ],
};
