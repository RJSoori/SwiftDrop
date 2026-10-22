"use client";

import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api";

export function PageHeading({ title, intro }: { title: string; intro?: string }) {
  return (
    <div className="max-w-2xl">
      <h1 className="display text-3xl sm:text-4xl">{title}</h1>
      {intro && <p className="mt-3 text-muted-foreground">{intro}</p>}
    </div>
  );
}

/**
 * Calls a service's protected /ping through the gateway and shows whether this account may use it.
 * Proves RBAC end to end in Phase 1; replace with real widgets as each service gains features.
 */
export function AccessCheck({ path, service }: { path: string; service: string }) {
  const ping = useQuery({ queryKey: ["ping", path], queryFn: () => api<Record<string, unknown>>(path), retry: false });
  const status = ping.isPending ? "checking" : ping.isSuccess ? "allowed" : "blocked";
  const code = ping.error instanceof ApiError ? ping.error.status : ping.isSuccess ? 200 : undefined;

  return (
    <div className="flex items-center justify-between gap-4 border-b py-3 last:border-b-0">
      <div>
        <p className="font-medium">{service}</p>
        <p className="text-sm text-muted-foreground">GET /api/v1{path}</p>
      </div>
      <p
        className={
          "shrink-0 rounded-full px-3 py-1 text-sm font-medium " +
          (status === "allowed"
            ? "bg-emerald-600/10 text-emerald-700 dark:text-emerald-300"
            : status === "blocked"
              ? "bg-destructive/10 text-destructive"
              : "bg-muted text-muted-foreground")
        }
      >
        {status === "checking" ? "Checking…" : status === "allowed" ? "Allowed" : "Blocked"}
        {code ? <span className="tabular ml-1.5 opacity-70">{code}</span> : null}
      </p>
    </div>
  );
}

/** The four service probes, so every dashboard shows what this role can and cannot reach. */
export function ServiceAccess() {
  return (
    <section className="mt-10 max-w-2xl rounded-xl border bg-card px-5 py-2">
      <h2 className="pt-3 text-base font-semibold">Service access for this account</h2>
      <AccessCheck service="Parcels" path="/parcels/ping" />
      <AccessCheck service="Dispatch" path="/dispatch/ping" />
      <AccessCheck service="Merchant tools" path="/merchant/ping" />
    </section>
  );
}
