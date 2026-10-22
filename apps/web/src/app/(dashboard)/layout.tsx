"use client";

import { ChevronDown, LogOut } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { Brand } from "@/components/brand";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { NAV, primaryRole, ROLE_LABEL } from "@/lib/roles";
import { useLogout, useMe } from "@/lib/session";

/** Shell for every role dashboard. middleware.ts has already checked the role; /me fills in the user. */
export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const me = useMe();
  const logout = useLogout();

  useEffect(() => {
    if (me.error) router.replace("/login"); // refresh already failed inside the API client
  }, [me.error, router]);

  if (!me.data) {
    return <p className="p-8 text-muted-foreground">Loading your dashboard…</p>;
  }

  const user = me.data;
  const role = primaryRole(user.roles);
  const nav = role ? NAV[role] : [];

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[16rem_1fr]">
      <aside className="bg-sidebar text-sidebar-foreground md:sticky md:top-0 md:h-dvh">
        <div className="flex items-center justify-between gap-4 px-5 py-4 md:block md:py-6">
          <Brand href={role ? nav[0].href : "/"} />
          {role && <p className="text-sm text-sidebar-foreground/70 md:mt-2">{ROLE_LABEL[role]} workspace</p>}
        </div>
        <nav aria-label="Dashboard" className="flex flex-wrap gap-1 px-3 pb-3 md:grid">
          {nav.map((item) => {
            const active = pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={
                  "shrink-0 rounded-md px-3 py-2 text-sm font-medium transition-colors " +
                  (active
                    ? "bg-sidebar-primary text-sidebar-primary-foreground"
                    : "hover:bg-sidebar-accent hover:text-sidebar-accent-foreground")
                }
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      </aside>

      <div className="min-w-0">
        <header className="flex items-center justify-end border-b px-5 py-3 md:px-8">
          <DropdownMenu>
            <DropdownMenuTrigger className="inline-flex max-w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm font-medium outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50">
              <span aria-hidden className="grid size-7 place-items-center rounded-full bg-brand text-xs font-bold text-primary-foreground">
                {initials(user.name)}
              </span>
              <span className="truncate">{user.name}</span>
              <ChevronDown aria-hidden className="size-4 text-muted-foreground" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-60">
              <DropdownMenuGroup>
                <DropdownMenuLabel className="text-sm">
                  <span className="block font-medium text-foreground">{user.name}</span>
                  <span className="block truncate">{user.email}</span>
                </DropdownMenuLabel>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={logout}>
                <LogOut aria-hidden />
                Log out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </header>
        <main className="px-5 py-8 md:px-8 md:py-10">{children}</main>
      </div>
    </div>
  );
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join("");
}
