"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeading } from "@/components/dashboard";
import { FormError } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, ApiError, type MerchantApplication } from "@/lib/api";

const dateFormat = new Intl.DateTimeFormat("en-LK", { dateStyle: "medium", timeStyle: "short" });

export default function MerchantApplicationsPage() {
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string>();
  const applications = useQuery({
    queryKey: ["merchants", "PENDING"],
    queryFn: () => api<MerchantApplication[]>("/auth/admin/merchants?status=PENDING"),
  });

  const review = useMutation({
    mutationFn: ({ id, action, reason }: { id: string; action: "approve" | "reject"; reason?: string }) =>
      api<MerchantApplication>(`/auth/admin/merchants/${id}/${action}`, {
        method: "POST",
        json: action === "reject" ? { reason } : undefined,
      }),
    onSuccess: (m, { action }) => {
      setNotice(`${m.businessName} ${action === "approve" ? "approved" : "rejected"}.`);
      return queryClient.invalidateQueries({ queryKey: ["merchants"] });
    },
  });

  const reject = (m: MerchantApplication) => {
    const reason = window.prompt(`Why are you rejecting ${m.businessName}? The reason is kept with the application.`);
    if (reason?.trim()) review.mutate({ id: m.id, action: "reject", reason: reason.trim() });
  };

  const error = review.error ?? applications.error;

  return (
    <>
      <PageHeading
        title="Merchant applications"
        intro="Check each business against its registration number before approving. Approved merchants get access the next time they log in."
      />

      <div className="mt-8 grid gap-4">
        <FormError message={error instanceof ApiError ? error.message : error ? "Could not load applications." : undefined} />
        {notice && !error && (
          <p role="status" className="text-sm text-muted-foreground">
            {notice}
          </p>
        )}

        {applications.isPending ? (
          <p className="text-muted-foreground">Loading applications…</p>
        ) : applications.data?.length === 0 ? (
          <div className="rounded-xl border border-dashed p-8 text-center text-muted-foreground">
            No applications waiting. New merchant signups appear here.
          </div>
        ) : (
          <div className="rounded-xl border bg-card [&_td]:px-4 [&_th]:px-4">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Business</TableHead>
                  <TableHead>Registration no.</TableHead>
                  <TableHead>Applicant</TableHead>
                  <TableHead>Applied</TableHead>
                  <TableHead className="text-right">Decision</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {applications.data?.map((m) => {
                  const busy = review.isPending && review.variables?.id === m.id;
                  return (
                    <TableRow key={m.id}>
                      <TableCell className="font-medium">{m.businessName}</TableCell>
                      <TableCell className="tabular">{m.registrationNo}</TableCell>
                      <TableCell>
                        <span className="block">{m.fullName}</span>
                        <span className="block text-muted-foreground">{m.email}</span>
                      </TableCell>
                      <TableCell className="tabular whitespace-nowrap">{dateFormat.format(new Date(m.createdAt))}</TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-2">
                          <Button size="lg" disabled={busy} onClick={() => review.mutate({ id: m.id, action: "approve" })}>
                            Approve
                          </Button>
                          <Button size="lg" variant="destructive" disabled={busy} onClick={() => reject(m)}>
                            Reject
                          </Button>
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
