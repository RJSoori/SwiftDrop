"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { PageHeading } from "@/components/dashboard";
import { applyServerErrors, FormError, FormField } from "@/components/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, ApiError, type ApiCredential, type CreatedApiCredential } from "@/lib/api";
import { createCredentialSchema, type CreateCredentialInput } from "@/lib/schemas";
import { useMe } from "@/lib/session";

const dateFormat = new Intl.DateTimeFormat("en-LK", { dateStyle: "medium", timeStyle: "short" });
const CREDENTIALS = "/auth/merchant/credentials";

const TYPE_LABEL = { API_KEY: "API key", CLIENT: "OAuth client" } as const;

export default function ApiKeysPage() {
  const me = useMe();
  const approved = me.data?.merchantStatus === "APPROVED";

  if (me.isPending) return <p className="text-muted-foreground">Loading…</p>;
  if (!approved) {
    return (
      <div className="max-w-2xl rounded-xl border-2 border-dashed border-tape bg-card p-6 sm:p-8">
        <PageHeading
          title="API keys are for approved merchants"
          intro="Once a SwiftDrop admin approves your account, log out and back in, then create keys here for your shop's system."
        />
      </div>
    );
  }
  return <ApiKeys />;
}

function ApiKeys() {
  const queryClient = useQueryClient();
  const [created, setCreated] = useState<CreatedApiCredential>();
  const [confirmRevoke, setConfirmRevoke] = useState<string>();
  const [notice, setNotice] = useState<string>();

  const list = useQuery({ queryKey: ["credentials"], queryFn: () => api<ApiCredential[]>(CREDENTIALS) });

  const form = useForm<CreateCredentialInput>({
    resolver: zodResolver(createCredentialSchema),
    defaultValues: { type: "API_KEY", name: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    setNotice(undefined);
    try {
      const result = await api<CreatedApiCredential>(CREDENTIALS, { method: "POST", json: values });
      setCreated(result);
      form.reset({ type: values.type, name: "" });
      await queryClient.invalidateQueries({ queryKey: ["credentials"] });
    } catch (error) {
      applyServerErrors(form, error);
    }
  });

  const revoke = useMutation({
    mutationFn: (id: string) => api(`${CREDENTIALS}/${id}`, { method: "DELETE" }),
    onSuccess: (_, id) => {
      const name = list.data?.find((c) => c.id === id)?.name;
      setConfirmRevoke(undefined);
      setNotice(`${name ?? "Credential"} revoked. Requests using it are refused from now on.`);
      return queryClient.invalidateQueries({ queryKey: ["credentials"] });
    },
  });

  const listError = revoke.error ?? list.error;

  return (
    <>
      <PageHeading
        title="API keys"
        intro="Let your shop's system create orders without a password. Each key or client can only create and read your own orders, and you can revoke it at any time."
      />

      {created && <SecretOnce created={created} onDone={() => setCreated(undefined)} />}

      <form onSubmit={onSubmit} noValidate className="mt-8 grid max-w-xl gap-5">
        <FormError message={errors.root?.message} />

        <fieldset className="grid gap-2">
          <legend className="mb-2 text-sm font-medium">Type</legend>
          <div className="flex flex-wrap gap-3">
            {(["API_KEY", "CLIENT"] as const).map((type) => (
              <Label
                key={type}
                className="cursor-pointer rounded-lg border px-4 py-2.5 has-checked:border-brand has-checked:bg-accent has-checked:text-accent-foreground"
              >
                <input type="radio" value={type} className="accent-brand" {...form.register("type")} />
                {TYPE_LABEL[type]}
              </Label>
            ))}
          </div>
          <p className="text-sm text-muted-foreground">
            An API key is sent as the <code>X-API-Key</code> header. An OAuth client swaps its id and secret for a
            15-minute token.
          </p>
          {errors.type && <p className="text-sm text-destructive">{errors.type.message}</p>}
        </fieldset>

        <FormField
          id="name"
          label="Name"
          placeholder="Shop backend"
          autoComplete="off"
          hint="Only you see this. Name it after the system that will use it."
          error={errors.name?.message}
          {...form.register("name")}
        />

        <Button type="submit" size="lg" disabled={isSubmitting} className="w-fit">
          {isSubmitting ? "Creating…" : "Create"}
        </Button>
      </form>

      <section className="mt-12 grid gap-4" aria-labelledby="keys-heading">
        <h2 id="keys-heading" className="text-lg font-semibold">
          Your keys and clients
        </h2>
        <FormError
          message={listError instanceof ApiError ? listError.message : listError ? "Could not load your keys." : undefined}
        />
        {notice && !listError && (
          <p role="status" className="text-sm text-muted-foreground">
            {notice}
          </p>
        )}

        {list.isPending ? (
          <p className="text-muted-foreground">Loading keys…</p>
        ) : list.data?.length === 0 ? (
          <div className="rounded-xl border border-dashed p-8 text-center text-muted-foreground">
            No keys yet. Create one above and give it to your shop&apos;s system.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border bg-card [&_td]:px-4 [&_th]:px-4">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Key prefix / client id</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>Last used</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {list.data?.map((c) => {
                  const busy = revoke.isPending && revoke.variables === c.id;
                  return (
                    <TableRow key={c.id} className={c.revokedAt ? "text-muted-foreground" : undefined}>
                      <TableCell className="font-medium">{c.name}</TableCell>
                      <TableCell>{TYPE_LABEL[c.type]}</TableCell>
                      <TableCell>
                        <code className="font-mono text-xs">
                          {c.type === "API_KEY" ? `${c.prefix}…` : c.prefix}
                        </code>
                      </TableCell>
                      <TableCell className="tabular whitespace-nowrap">{dateFormat.format(new Date(c.createdAt))}</TableCell>
                      <TableCell className="tabular whitespace-nowrap">
                        {c.lastUsedAt ? dateFormat.format(new Date(c.lastUsedAt)) : "Never"}
                      </TableCell>
                      <TableCell>
                        {c.revokedAt ? (
                          <Badge variant="destructive" title={`Revoked ${dateFormat.format(new Date(c.revokedAt))}`}>
                            Revoked
                          </Badge>
                        ) : (
                          <Badge variant="secondary">Active</Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        {!c.revokedAt && (
                          <div className="flex justify-end gap-2">
                            {confirmRevoke === c.id ? (
                              <>
                                <Button size="lg" variant="destructive" disabled={busy} onClick={() => revoke.mutate(c.id)}>
                                  {busy ? "Revoking…" : "Confirm revoke"}
                                </Button>
                                <Button size="lg" variant="outline" disabled={busy} onClick={() => setConfirmRevoke(undefined)}>
                                  Cancel
                                </Button>
                              </>
                            ) : (
                              <Button size="lg" variant="outline" onClick={() => setConfirmRevoke(c.id)}>
                                Revoke
                              </Button>
                            )}
                          </div>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </section>
    </>
  );
}

/** Shows a new secret exactly once. Dismissing it drops the secret from memory; it cannot be fetched again. */
function SecretOnce({ created, onDone }: { created: CreatedApiCredential; onDone: () => void }) {
  const { credential, secret } = created;
  const isClient = credential.type === "CLIENT";
  return (
    <div
      role="alertdialog"
      aria-labelledby="secret-title"
      aria-describedby="secret-help"
      className="mt-8 grid max-w-2xl gap-4 rounded-xl border-2 border-brand bg-card p-6"
    >
      <div>
        <h2 id="secret-title" className="text-lg font-semibold">
          Copy your {isClient ? "client secret" : "API key"} now
        </h2>
        <p id="secret-help" className="mt-1 text-sm text-muted-foreground">
          This is the only time it is shown. SwiftDrop keeps only a fingerprint of it, so if you lose it you will need
          to create a new one and revoke this one.
        </p>
      </div>

      {isClient && <CopyRow label="Client id" value={credential.clientId ?? credential.prefix} />}
      <CopyRow label={isClient ? "Client secret" : "API key"} value={secret} />

      <p className="text-sm text-muted-foreground">
        {isClient ? (
          <>
            Get a token with <code>POST /api/v1/auth/oauth/token</code>, form field{" "}
            <code>grant_type=client_credentials</code> and the id and secret as HTTP Basic credentials.
          </>
        ) : (
          <>
            Send it on every order request as the <code>X-API-Key</code> header.
          </>
        )}
      </p>

      <Button size="lg" className="w-fit" onClick={onDone}>
        I have stored it safely
      </Button>
    </div>
  );
}

function CopyRow({ label, value }: { label: string; value: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };
  return (
    <div className="grid gap-1.5">
      <span className="text-sm font-medium">{label}</span>
      <div className="flex flex-wrap items-center gap-2">
        <code className="min-w-0 flex-1 rounded-md border bg-muted px-3 py-2 font-mono text-sm break-all select-all">
          {value}
        </code>
        <Button type="button" variant="outline" size="lg" onClick={copy} aria-label={`Copy ${label.toLowerCase()}`}>
          {copied ? "Copied" : "Copy"}
        </Button>
      </div>
    </div>
  );
}
