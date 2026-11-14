"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeading } from "@/components/dashboard";
import { FormError } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, ApiError, type AuditLogEntry, type Page } from "@/lib/api";

const dateFormat = new Intl.DateTimeFormat("en-LK", { dateStyle: "medium", timeStyle: "medium" });
const PAGE_SIZE = 50;

/** Every action auth-service records. Keep in step with the AuditLogEntry action strings in auth-service. */
const ACTIONS: { value: string; label: string }[] = [
  { value: "MERCHANT_APPROVED", label: "Merchant approved" },
  { value: "MERCHANT_REJECTED", label: "Merchant rejected" },
  { value: "MERCHANT_SUSPENDED", label: "Merchant suspended" },
  { value: "MERCHANT_REINSTATED", label: "Merchant reinstated" },
  { value: "STAFF_CREATED", label: "Staff account created" },
  { value: "USER_DISABLED", label: "User disabled" },
  { value: "USER_ENABLED", label: "User enabled" },
  { value: "API_CREDENTIAL_CREATED", label: "API key or client created" },
  { value: "API_CREDENTIAL_REVOKED", label: "API key or client revoked" },
];
const ACTION_LABEL = Object.fromEntries(ACTIONS.map((a) => [a.value, a.label]));

export default function AuditLogPage() {
  const [action, setAction] = useState("");
  const [page, setPage] = useState(0);

  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (action) params.set("action", action);

  const log = useQuery({
    queryKey: ["audit-logs", action, page],
    queryFn: () => api<Page<AuditLogEntry>>(`/auth/admin/audit-logs?${params}`),
    placeholderData: keepPreviousData,
  });
  const data = log.data;

  return (
    <>
      <PageHeading
        title="Audit log"
        intro="Every change made by an admin, and every API key a merchant creates or revokes, newest first. Entries cannot be edited or deleted."
      />

      <div className="mt-8 grid gap-1.5 sm:w-72">
        <Label htmlFor="filter-action">Action</Label>
        <select
          id="filter-action"
          className="h-10 rounded-md border bg-background px-3 text-sm"
          value={action}
          onChange={(e) => {
            setAction(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All actions</option>
          {ACTIONS.map((a) => (
            <option key={a.value} value={a.value}>
              {a.label}
            </option>
          ))}
        </select>
      </div>

      <div className="mt-6 grid gap-4">
        <FormError message={log.error instanceof ApiError ? log.error.message : log.error ? "Could not load the audit log." : undefined} />

        {log.isPending ? (
          <p className="text-muted-foreground">Loading the audit log…</p>
        ) : data?.content.length === 0 ? (
          <div className="rounded-xl border border-dashed p-8 text-center text-muted-foreground">No entries yet.</div>
        ) : (
          <>
            <div className="overflow-x-auto rounded-xl border bg-card [&_td]:px-4 [&_th]:px-4">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>When</TableHead>
                    <TableHead>Who</TableHead>
                    <TableHead>Action</TableHead>
                    <TableHead>Target</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data?.content.map((e) => (
                    <TableRow key={e.id}>
                      <TableCell className="tabular whitespace-nowrap">{dateFormat.format(new Date(e.occurredAt))}</TableCell>
                      <TableCell>{e.actorEmail ?? (e.actorId ? "Deleted account" : "System")}</TableCell>
                      <TableCell>{ACTION_LABEL[e.action] ?? e.action}</TableCell>
                      <TableCell>
                        <code className="font-mono text-xs break-all">{e.target}</code>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            {data && data.totalPages > 1 && (
              <nav aria-label="Pages" className="flex items-center gap-3 text-sm">
                <Button size="lg" variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>
                  Newer
                </Button>
                <span className="tabular">
                  Page {data.page + 1} of {data.totalPages}
                </span>
                <Button size="lg" variant="outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
                  Older
                </Button>
              </nav>
            )}
          </>
        )}
      </div>
    </>
  );
}
