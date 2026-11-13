"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeading } from "@/components/dashboard";
import { FormError } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, ApiError, type MerchantApplication, type MerchantStatus } from "@/lib/api";

const dateFormat = new Intl.DateTimeFormat("en-LK", { dateStyle: "medium", timeStyle: "short" });

type Tab = Extract<MerchantStatus, "PENDING" | "APPROVED" | "SUSPENDED">;
type Action = "approve" | "reject" | "suspend" | "reinstate";

const TABS: { status: Tab; label: string; empty: string }[] = [
  { status: "PENDING", label: "Pending", empty: "No applications waiting. New merchant signups appear here." },
  { status: "APPROVED", label: "Approved", empty: "No approved merchants yet." },
  { status: "SUSPENDED", label: "Suspended", empty: "No suspended merchants." },
];

const DONE: Record<Action, string> = {
  approve: "approved",
  reject: "rejected",
  suspend: "suspended. Its API keys stop working until it is reinstated",
  reinstate: "reinstated. Its API keys work again",
};

export default function MerchantsPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("PENDING");
  const [notice, setNotice] = useState<string>();
  const [confirmSuspend, setConfirmSuspend] = useState<string>();

  const merchants = useQuery({
    queryKey: ["merchants", tab],
    queryFn: () => api<MerchantApplication[]>(`/auth/admin/merchants?status=${tab}`),
  });

  const act = useMutation({
    mutationFn: ({ id, action, reason }: { id: string; action: Action; reason?: string }) =>
      api<MerchantApplication>(`/auth/admin/merchants/${id}/${action}`, {
        method: "POST",
        json: action === "reject" ? { reason } : undefined,
      }),
    onSuccess: (m, { action }) => {
      setConfirmSuspend(undefined);
      setNotice(`${m.businessName} ${DONE[action]}.`);
      return queryClient.invalidateQueries({ queryKey: ["merchants"] });
    },
  });

  const reject = (m: MerchantApplication) => {
    const reason = window.prompt(`Why are you rejecting ${m.businessName}? The reason is kept with the application.`);
    if (reason?.trim()) act.mutate({ id: m.id, action: "reject", reason: reason.trim() });
  };

  const error = act.error ?? merchants.error;
  const current = TABS.find((t) => t.status === tab)!;

  return (
    <>
      <PageHeading
        title="Merchants"
        intro="Check each business against its registration number before approving. Approved merchants get access the next time they log in; suspending one stops its API keys at once."
      />

      <div role="tablist" aria-label="Merchant status" className="mt-8 flex flex-wrap gap-2">
        {TABS.map((t) => (
          <Button
            key={t.status}
            role="tab"
            aria-selected={tab === t.status}
            variant={tab === t.status ? "default" : "outline"}
            size="lg"
            onClick={() => {
              setTab(t.status);
              setNotice(undefined);
              setConfirmSuspend(undefined);
            }}
          >
            {t.label}
          </Button>
        ))}
      </div>

      <div role="tabpanel" className="mt-6 grid gap-4">
        <FormError message={error instanceof ApiError ? error.message : error ? "Could not load merchants." : undefined} />
        {notice && !error && (
          <p role="status" className="text-sm text-muted-foreground">
            {notice}
          </p>
        )}

        {merchants.isPending ? (
          <p className="text-muted-foreground">Loading merchants…</p>
        ) : merchants.data?.length === 0 ? (
          <div className="rounded-xl border border-dashed p-8 text-center text-muted-foreground">{current.empty}</div>
        ) : (
          <div className="overflow-x-auto rounded-xl border bg-card [&_td]:px-4 [&_th]:px-4">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Business</TableHead>
                  <TableHead>Registration no.</TableHead>
                  <TableHead>Applicant</TableHead>
                  <TableHead>{tab === "PENDING" ? "Applied" : "Reviewed"}</TableHead>
                  <TableHead className="text-right">{tab === "PENDING" ? "Decision" : <span className="sr-only">Actions</span>}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {merchants.data?.map((m) => {
                  const busy = act.isPending && act.variables?.id === m.id;
                  const when = tab === "PENDING" ? m.createdAt : (m.reviewedAt ?? m.createdAt);
                  return (
                    <TableRow key={m.id}>
                      <TableCell className="font-medium">{m.businessName}</TableCell>
                      <TableCell className="tabular">{m.registrationNo}</TableCell>
                      <TableCell>
                        <span className="block">{m.fullName}</span>
                        <span className="block text-muted-foreground">{m.email}</span>
                      </TableCell>
                      <TableCell className="tabular whitespace-nowrap">{dateFormat.format(new Date(when))}</TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-2">
                          {tab === "PENDING" && (
                            <>
                              <Button size="lg" disabled={busy} onClick={() => act.mutate({ id: m.id, action: "approve" })}>
                                Approve
                              </Button>
                              <Button size="lg" variant="destructive" disabled={busy} onClick={() => reject(m)}>
                                Reject
                              </Button>
                            </>
                          )}
                          {tab === "APPROVED" &&
                            (confirmSuspend === m.id ? (
                              <>
                                <Button
                                  size="lg"
                                  variant="destructive"
                                  disabled={busy}
                                  onClick={() => act.mutate({ id: m.id, action: "suspend" })}
                                >
                                  {busy ? "Suspending…" : "Confirm suspend"}
                                </Button>
                                <Button size="lg" variant="outline" disabled={busy} onClick={() => setConfirmSuspend(undefined)}>
                                  Cancel
                                </Button>
                              </>
                            ) : (
                              <Button size="lg" variant="outline" onClick={() => setConfirmSuspend(m.id)}>
                                Suspend
                              </Button>
                            ))}
                          {tab === "SUSPENDED" && (
                            <Button size="lg" disabled={busy} onClick={() => act.mutate({ id: m.id, action: "reinstate" })}>
                              Reinstate
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </div>
    </>
  );
}
