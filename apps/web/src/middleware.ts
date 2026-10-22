import { NextResponse, type NextRequest } from "next/server";
import { homeFor, roleForPath } from "./lib/roles";

/**
 * Page routing only: unauthenticated -> /login, wrong role -> own dashboard.
 * The JWT is decoded, not verified, here. It only decides which shell to render; every API call is verified
 * by the gateway and the services. The cookie may hold an expired token: the API client refreshes it.
 */
export function middleware(request: NextRequest) {
  const required = roleForPath(request.nextUrl.pathname);
  if (!required) return NextResponse.next();

  const roles = rolesFromToken(request.cookies.get("sd_access")?.value);
  if (!roles) return NextResponse.redirect(new URL("/login", request.url));
  if (!roles.includes(required)) return NextResponse.redirect(new URL(homeFor(roles), request.url));
  return NextResponse.next();
}

function rolesFromToken(token: string | undefined): string[] | null {
  const payload = token?.split(".")[1];
  if (!payload) return null;
  try {
    const json = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return Array.isArray(json.roles) ? json.roles : null;
  } catch {
    return null;
  }
}

export const config = {
  matcher: ["/customer/:path*", "/merchant/:path*", "/courier/:path*", "/dispatcher/:path*", "/admin/:path*"],
};
