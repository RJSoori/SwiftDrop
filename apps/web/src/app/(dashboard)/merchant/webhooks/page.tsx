"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeading } from "@/components/dashboard";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { api, ApiError, WebhookDelivery, WebhookEndpoint } from "@/lib/api";
import {
  Radio,
  RotateCcw,
  Copy,
  Check,
  RefreshCw,
  Play,
  X,
  ShieldCheck,
} from "lucide-react";

const dateFormat = new Intl.DateTimeFormat("en-LK", { dateStyle: "medium", timeStyle: "short" });

export default function MerchantWebhooksPage() {
  const queryClient = useQueryClient();
  const [urlInput, setUrlInput] = useState("");
  const [urlInitialized, setUrlInitialized] = useState(false);
  const [statusMessage, setStatusMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  // Secret Reveal Modal
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // Replay
  const [replayingId, setReplayingId] = useState<string | null>(null);

  // Queries
  const endpointQuery = useQuery({
    queryKey: ["merchant-webhook-endpoint"],
    queryFn: async () => {
      const ep = await api<WebhookEndpoint>("/merchant/webhook");
      if (ep?.url && !urlInitialized) {
        setUrlInput(ep.url);
        setUrlInitialized(true);
      }
      return ep;
    },
  });

  const deliveriesQuery = useQuery({
    queryKey: ["merchant-webhook-deliveries"],
    queryFn: () => api<{ content: WebhookDelivery[] }>("/merchant/webhook/deliveries"),
  });

  // Mutations
  const saveEndpointMutation = useMutation({
    mutationFn: (url: string) =>
      api<WebhookEndpoint>("/merchant/webhook", {
        method: "PUT",
        json: { url },
      }),
    onSuccess: (res) => {
      setStatusMessage("Webhook endpoint configured successfully.");
      setErrorMessage("");
      if (res.secret) {
        setRevealedSecret(res.secret);
      }
      queryClient.invalidateQueries({ queryKey: ["merchant-webhook-endpoint"] });
    },
    onError: (err: unknown) => {
      setErrorMessage(err instanceof ApiError ? err.message : "Failed to update webhook configuration.");
    },
  });

  const rotateSecretMutation = useMutation({
    mutationFn: () =>
      api<WebhookEndpoint>("/merchant/webhook/rotate-secret", {
        method: "POST",
      }),
    onSuccess: (res) => {
      setStatusMessage("Secret rotated successfully. Update your signature verification receiver.");
      setErrorMessage("");
      if (res.secret) {
        setRevealedSecret(res.secret);
      }
      queryClient.invalidateQueries({ queryKey: ["merchant-webhook-endpoint"] });
    },
    onError: (err: unknown) => {
      setErrorMessage(err instanceof ApiError ? err.message : "Failed to rotate webhook secret.");
    },
  });

  const replayMutation = useMutation({
    mutationFn: (deliveryId: string) =>
      api(`/merchant/webhook/deliveries/${deliveryId}/replay`, {
        method: "POST",
      }),
    onMutate: (id) => setReplayingId(id),
    onSuccess: () => {
      setStatusMessage("Webhook delivery re-enqueued for transmission.");
      queryClient.invalidateQueries({ queryKey: ["merchant-webhook-deliveries"] });
    },
    onError: (err: unknown) => {
      setErrorMessage(err instanceof ApiError ? err.message : "Failed to replay webhook.");
    },
    onSettled: () => setReplayingId(null),
  });

  const endpoint = endpointQuery.data;
  const deliveries = deliveriesQuery.data?.content || [];

  const handleCopy = () => {
    if (revealedSecret) {
      navigator.clipboard.writeText(revealedSecret);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <PageHeading
          title="Webhook Settings"
          intro="Receive automated real-time tracking updates directly in your online store system."
        />
        <Button
          variant="outline"
          onClick={() => {
            queryClient.invalidateQueries({ queryKey: ["merchant-webhook-endpoint"] });
            queryClient.invalidateQueries({ queryKey: ["merchant-webhook-deliveries"] });
          }}
          disabled={endpointQuery.isFetching || deliveriesQuery.isFetching}
        >
          <RefreshCw className={`mr-2 h-4 w-4 ${deliveriesQuery.isFetching ? "animate-spin" : ""}`} />
          Refresh
        </Button>
      </div>

      {statusMessage && (
        <div className="p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 flex items-center justify-between text-sm">
          <span>{statusMessage}</span>
          <Button variant="ghost" size="icon" onClick={() => setStatusMessage("")}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {errorMessage && (
        <div className="p-4 rounded-xl border border-destructive/20 bg-destructive/10 text-destructive flex items-center justify-between text-sm">
          <span>{errorMessage}</span>
          <Button variant="ghost" size="icon" onClick={() => setErrorMessage("")}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* Endpoint Configuration Card */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="md:col-span-2">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>Endpoint Configuration</CardTitle>
                <CardDescription>
                  SwiftDrop dispatches signed HTTP POST events whenever parcel status changes.
                </CardDescription>
              </div>
              <Badge variant={endpoint?.active ? "default" : "outline"}>
                {endpoint?.active ? "Active" : "Inactive"}
              </Badge>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <form
              onSubmit={(e) => {
                e.preventDefault();
                saveEndpointMutation.mutate(urlInput);
              }}
              className="space-y-4"
            >
              <div className="space-y-1.5">
                <Label htmlFor="webhookUrl">Webhook Target URL</Label>
                <Input
                  id="webhookUrl"
                  type="url"
                  placeholder="https://your-store.com/api/webhooks/swiftdrop"
                  required
                  value={urlInput}
                  onChange={(e) => setUrlInput(e.target.value)}
                />
                <p className="text-xs text-muted-foreground mt-1">
                  For local testing with the Demo Store, use: <code className="tabular font-mono text-xs">http://demo-store:4000/webhook</code>
                </p>
              </div>

              <div className="flex gap-2">
                <Button type="submit" disabled={saveEndpointMutation.isPending}>
                  {saveEndpointMutation.isPending ? "Saving..." : "Save Endpoint"}
                </Button>
                {endpoint?.active && (
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => {
                      if (confirm("Rotate webhook secret? Your existing signature verification secret will be invalidated immediately.")) {
                        rotateSecretMutation.mutate();
                      }
                    }}
                    disabled={rotateSecretMutation.isPending}
                  >
                    <RotateCcw className="mr-2 h-4 w-4" />
                    Rotate Secret
                  </Button>
                )}
              </div>
            </form>
          </CardContent>
        </Card>

        {/* Security / Signature Info Card */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-primary" />
              HMAC-SHA256 Security
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs space-y-3 text-muted-foreground">
            <p>Every webhook payload is cryptographically signed using your secret key.</p>
            <div>
              <p className="font-semibold text-foreground">Header:</p>
              <code className="block mt-1 p-2 rounded bg-muted text-foreground tabular font-mono text-[11px] break-all">
                X-SwiftDrop-Signature: t=1731450000,v1=5d41402abc...
              </code>
            </div>
            <p>
              Verify signature by computing HMAC-SHA256 of <code className="font-mono text-foreground">t + "." + payload</code> and comparing against <code className="font-mono text-foreground">v1</code>.
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Delivery Audit Logs */}
      <Card>
        <CardHeader>
          <CardTitle>Delivery Audit Logs</CardTitle>
          <CardDescription>
            Historical record of transmitted webhook events and response codes.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {deliveriesQuery.isPending ? (
            <p className="py-8 text-center text-muted-foreground">Loading delivery history…</p>
          ) : deliveries.length === 0 ? (
            <div className="rounded-xl border border-dashed p-8 text-center text-muted-foreground">
              <Radio className="mx-auto h-8 w-8 opacity-40 mb-2" />
              <p className="font-medium">No webhook deliveries recorded yet.</p>
              <p className="text-xs mt-1">Status changes on your orders will trigger automated dispatches here.</p>
            </div>
          ) : (
            <div className="rounded-xl border bg-card [&_td]:px-4 [&_th]:px-4">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Event ID</TableHead>
                    <TableHead>State</TableHead>
                    <TableHead>HTTP Code</TableHead>
                    <TableHead>Attempt</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead className="text-right">Action</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {deliveries.map((del) => (
                    <TableRow key={del.id}>
                      <TableCell className="tabular font-mono text-xs font-medium">
                        {del.eventId}
                      </TableCell>
                      <TableCell>
                        <Badge variant={del.state === "DELIVERED" ? "default" : del.state === "PENDING" ? "secondary" : "destructive"}>
                          {del.state}
                        </Badge>
                      </TableCell>
                      <TableCell className="tabular font-mono text-sm">
                        {del.statusCode ? (
                          <span className={del.statusCode >= 200 && del.statusCode < 300 ? "text-emerald-600 dark:text-emerald-400 font-bold" : "text-destructive font-bold"}>
                            {del.statusCode}
                          </span>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>
                      <TableCell className="tabular font-mono text-xs">
                        #{del.attempt}
                      </TableCell>
                      <TableCell className="tabular font-mono text-xs text-muted-foreground whitespace-nowrap">
                        {del.deliveredAt ? dateFormat.format(new Date(del.deliveredAt)) : dateFormat.format(new Date(del.createdAt))}
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => replayMutation.mutate(del.id)}
                          disabled={replayingId === del.id}
                        >
                          <Play className={`mr-1 h-3.5 w-3.5 ${replayingId === del.id ? "animate-spin" : ""}`} />
                          Replay
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Secret Reveal Modal */}
      {revealedSecret && (
        <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-4">
          <Card className="w-full max-w-md shadow-2xl">
            <CardHeader className="flex flex-row items-center justify-between pb-4 border-b">
              <div>
                <CardTitle className="text-lg">Webhook Signing Secret</CardTitle>
                <CardDescription>Copy this key now. It will not be shown in full again.</CardDescription>
              </div>
              <Button variant="ghost" size="icon" onClick={() => setRevealedSecret(null)}>
                <X className="h-4 w-4" />
              </Button>
            </CardHeader>
            <CardContent className="p-6 space-y-4">
              <div className="p-3 bg-muted rounded-lg border flex items-center justify-between">
                <code className="tabular font-mono text-sm break-all font-semibold select-all text-foreground">
                  {revealedSecret}
                </code>
                <Button variant="ghost" size="icon" className="shrink-0 ml-2" onClick={handleCopy}>
                  {copied ? <Check className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4" />}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                Store this secret in your receiver application (e.g. as <code className="font-mono">SWIFTDROP_WEBHOOK_SECRET</code> in the Demo Store).
              </p>
              <div className="flex justify-end pt-2">
                <Button onClick={() => setRevealedSecret(null)}>Done</Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
