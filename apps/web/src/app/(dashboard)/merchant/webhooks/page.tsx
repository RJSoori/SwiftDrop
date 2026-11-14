"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { ApiKey, WebhookDelivery, WebhookEndpoint } from "@/lib/merchant";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export default function MerchantIntegrations() {
  const client = useQueryClient();
  const [secret, setSecret] = useState<string | null>(null);
  const [apiKey, setApiKey] = useState<string | null>(null);
  const endpoint = useQuery({
    queryKey: ["merchant-webhook"],
    queryFn: () => api<WebhookEndpoint>("/merchant/webhook"),
    retry: 1,
  });
  const deliveries = useQuery({
    queryKey: ["merchant-webhook-deliveries"],
    queryFn: () => api<WebhookDelivery[]>("/merchant/webhook/deliveries"),
    refetchInterval: 5000,
    retry: 1,
  });
  const keys = useQuery({
    queryKey: ["merchant-api-keys"],
    queryFn: () => api<ApiKey[]>("/auth/merchant/api-keys"),
    retry: 1,
  });
  const configure = useMutation({
    mutationFn: (url: string) =>
      api<{ secret: string }>("/merchant/webhook", {
        method: "PUT",
        json: { url },
      }),
    onSuccess: (data) => {
      setSecret(data.secret);
      void client.invalidateQueries({ queryKey: ["merchant-webhook"] });
    },
  });
  const rotate = useMutation({
    mutationFn: () =>
      api<{ secret: string }>("/merchant/webhook/rotate-secret", {
        method: "POST",
      }),
    onSuccess: (data) => setSecret(data.secret),
  });
  const createKey = useMutation({
    mutationFn: () =>
      api<{ apiKey: string }>("/auth/merchant/api-keys", { method: "POST" }),
    onSuccess: (data) => {
      setApiKey(data.apiKey);
      void client.invalidateQueries({ queryKey: ["merchant-api-keys"] });
    },
  });
  const revoke = useMutation({
    mutationFn: (id: string) =>
      api("/auth/merchant/api-keys/" + id, { method: "DELETE" }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["merchant-api-keys"] });
    },
  });
  const replay = useMutation({
    mutationFn: (id: string) =>
      api("/merchant/webhook/deliveries/" + id + "/replay", { method: "POST" }),
    onSuccess: () => {
      void client.invalidateQueries({
        queryKey: ["merchant-webhook-deliveries"],
      });
    },
  });
  return (
    <div className="space-y-8">
      <PageHeading
        title="Shop integration"
        intro="Connect your shop to SwiftDrop and receive signed delivery updates."
      />
      <section className="max-w-3xl rounded-xl border bg-card p-6">
        <h2 className="text-xl font-semibold">API keys</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Use a key on your shop server to create orders. Each full key is shown
          once. Revocation can take up to 60 seconds at the gateway.
        </p>
        {keys.isError && (
          <p role="alert" className="mt-3 text-destructive">
            {keys.error.message}
          </p>
        )}
        <Button
          className="mt-4"
          disabled={createKey.isPending || !!apiKey}
          onClick={() => createKey.mutate()}
        >
          Create API key
        </Button>
        {apiKey && (
          <div className="mt-4 rounded-lg border border-tape bg-muted p-4">
            <p className="font-medium">
              Copy this key now. It will not be shown again.
            </p>
            <code className="mt-3 block break-all select-all">{apiKey}</code>
            <Button
              className="mt-3"
              variant="outline"
              onClick={() => setApiKey(null)}
            >
              I have saved it
            </Button>
          </div>
        )}
        <ul className="mt-4 divide-y">
          {keys.data?.map((key) => (
            <li
              key={key.id}
              className="flex flex-wrap items-center justify-between gap-3 py-3"
            >
              <code>{key.prefix}...</code>
              <span className="text-sm text-muted-foreground">
                {key.revokedAt ? "Revoked" : "Active"}
              </span>
              {!key.revokedAt && (
                <Button
                  variant="outline"
                  disabled={revoke.isPending}
                  onClick={() => revoke.mutate(key.id)}
                >
                  Revoke
                </Button>
              )}
            </li>
          ))}
        </ul>
        {(createKey.error || revoke.error) && (
          <p role="alert" className="mt-3 text-destructive">
            {(createKey.error || revoke.error)?.message}
          </p>
        )}
      </section>
      <section className="max-w-3xl rounded-xl border bg-card p-6">
        <h2 className="text-xl font-semibold">Webhook callback</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          SwiftDrop signs every delivery event with your secret and sends it to
          your endpoint. Your server must verify the signature.
        </p>
        {endpoint.isPending ? (
          <p className="mt-5">Loading webhook configuration...</p>
        ) : endpoint.isError ? (
          <p className="mt-5 text-destructive" role="alert">
            {endpoint.error.message}
          </p>
        ) : (
          <form
            className="mt-5 space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              setSecret(null);
              const data = new FormData(e.currentTarget);
              configure.mutate(
                String(data.get("url")),
              );
            }}
          >
            <label className="block text-sm font-medium" htmlFor="webhook-url">
              Endpoint URL
            </label>
            <Input
              id="webhook-url"
              name="url"
              type="url"
              required
              maxLength={2048}
              defaultValue={endpoint.data.url ?? ""}
              placeholder="https://your-shop.example/webhooks/swiftdrop"
            />
            <div className="flex flex-wrap gap-3">
              <Button
                type="submit"
                disabled={configure.isPending || rotate.isPending}
              >
                Save endpoint
              </Button>
              {endpoint.data.url && (
                <Button
                  type="button"
                  variant="outline"
                  disabled={rotate.isPending || configure.isPending}
                  onClick={() => {
                    setSecret(null);
                    rotate.mutate();
                  }}
                >
                  Rotate secret
                </Button>
              )}
            </div>
          </form>
        )}
        {secret && (
          <div className="mt-4 rounded-lg border border-tape bg-muted p-4">
            <p className="font-medium">
              Copy this signing secret now. It will not be shown again.
            </p>
            <code className="mt-3 block break-all select-all">{secret}</code>
            <Button
              className="mt-3"
              variant="outline"
              onClick={() => setSecret(null)}
            >
              I have saved it
            </Button>
          </div>
        )}
        {(configure.error || rotate.error) && (
          <p className="mt-3 text-destructive" role="alert">
            {(configure.error || rotate.error)?.message}
          </p>
        )}
      </section>
      <section>
        <h2 className="text-xl font-semibold">Webhook deliveries</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Latest 50 deliveries. Failed callbacks retry four times; dead
          deliveries can be replayed with a fresh signature.
        </p>
        {replay.error && (
          <p role="alert" className="mt-3 text-destructive">
            {replay.error.message}
          </p>
        )}
        {deliveries.isPending ? (
          <p className="mt-5">Loading deliveries...</p>
        ) : deliveries.isError ? (
          <p className="mt-5 text-destructive" role="alert">
            {deliveries.error.message}
          </p>
        ) : deliveries.data.length === 0 ? (
          <p className="mt-5 rounded-xl border border-dashed p-8 text-muted-foreground">
            No webhook deliveries yet. Configure an endpoint, then change a
            merchant parcel status.
          </p>
        ) : (
          <div className="mt-5 overflow-x-auto rounded-xl border">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted">
                <tr>
                  {[
                    "Event ID",
                    "Attempts",
                    "HTTP status",
                    "State",
                    "Last result",
                    "Action",
                  ].map((h) => (
                    <th
                      key={h}
                      className="px-4 py-3 font-medium whitespace-nowrap"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {deliveries.data.map((delivery) => (
                  <tr key={delivery.id} className="border-t">
                    <td className="px-4 py-3 font-mono text-xs">
                      {delivery.eventId}
                    </td>
                    <td className="px-4 py-3">{delivery.attempt}</td>
                    <td className="px-4 py-3">{delivery.statusCode ?? "—"}</td>
                    <td className="px-4 py-3">{delivery.state}</td>
                    <td className="px-4 py-3">
                      {delivery.lastError ??
                        (delivery.deliveredAt
                          ? new Date(delivery.deliveredAt).toLocaleString()
                          : "Waiting")}
                    </td>
                    <td className="px-4 py-3">
                      {delivery.state === "DEAD" && (
                        <Button
                          variant="outline"
                          disabled={replay.isPending}
                          onClick={() => replay.mutate(delivery.id)}
                        >
                          Replay
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
