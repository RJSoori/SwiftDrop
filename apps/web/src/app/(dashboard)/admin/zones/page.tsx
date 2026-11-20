"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Undo2, X } from "lucide-react";
import { useMemo, useState } from "react";
import { PageHeading } from "@/components/dashboard";
import { FormError } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ZoneMap } from "@/components/zones/zone-map";
import { PricingRules, ZoneEditor } from "@/components/zones/zone-forms";
import { api, ApiError } from "@/lib/api";
import { formatLkr, pointsFromPolygon, STRATEGY_LABEL, type LatLng, type Zone } from "@/lib/zones";

type Mode = { kind: "view" } | { kind: "create" } | { kind: "edit"; zone: Zone };

/** UC-07: draw delivery zones on the map and set the pricing rule each one uses. */
export default function ZonesPage() {
  const queryClient = useQueryClient();
  const zones = useQuery({ queryKey: ["zones", "admin"], queryFn: () => api<Zone[]>("/admin/zones") });
  const [selectedId, setSelectedId] = useState<string>();
  const [mode, setMode] = useState<Mode>({ kind: "view" });
  const [draft, setDraft] = useState<LatLng[]>([]);
  const [notice, setNotice] = useState<string>();

  const selected = zones.data?.find((z) => z.id === selectedId);
  const drawing = mode.kind !== "view";
  const counts = useMemo(() => {
    const active = zones.data?.filter((z) => z.active).length ?? 0;
    return { active, inactive: (zones.data?.length ?? 0) - active };
  }, [zones.data]);

  // Stable between renders, so the map only redraws (and re-zooms) when the zones really change.
  const editingId = mode.kind === "edit" ? mode.zone.id : undefined;
  const mapZones = useMemo(() => (zones.data ?? []).filter((z) => z.id !== editingId), [zones.data, editingId]);

  const toggle = useMutation({
    mutationFn: (zone: Zone) =>
      api<Zone>(`/admin/zones/${zone.id}/status`, { method: "PATCH", json: { active: !zone.active } }),
    onSuccess: (zone) => {
      setNotice(`${zone.name} is now ${zone.active ? "active and offered for quotes" : "inactive and no longer offered for quotes"}.`);
      return queryClient.invalidateQueries({ queryKey: ["zones"] });
    },
  });

  const startCreate = () => {
    setNotice(undefined);
    setSelectedId(undefined);
    setDraft([]);
    setMode({ kind: "create" });
  };
  const startEdit = (zone: Zone) => {
    setNotice(undefined);
    setDraft(pointsFromPolygon(zone.boundary));
    setMode({ kind: "edit", zone });
  };
  const stopDrawing = () => {
    setDraft([]);
    setMode({ kind: "view" });
  };

  return (
    <>
      <PageHeading
        title="Zones & pricing"
        intro="Each zone is an area on the map with its own pricing rule. A quote is priced by the zone the parcel is delivered to; places outside every active zone cannot be booked."
      />

      <div className="mt-8 grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <Card>
          <CardHeader className="border-b">
            <CardTitle>{drawing ? (mode.kind === "create" ? "Draw the new zone" : `Redraw ${mode.zone.name}`) : "Delivery zones"}</CardTitle>
            <CardDescription>
              {drawing
                ? "Click the map to place each corner. Zones may share edges but must not overlap."
                : `${counts.active} active${counts.inactive ? `, ${counts.inactive} inactive` : ""}. Select a zone on the map or in the list.`}
            </CardDescription>
            <div className="mt-2 flex flex-wrap gap-2">
              {drawing ? (
                <>
                  <Button variant="outline" disabled={draft.length === 0} onClick={() => setDraft((d) => d.slice(0, -1))}>
                    <Undo2 aria-hidden /> Undo point
                  </Button>
                  <Button variant="outline" disabled={draft.length === 0} onClick={() => setDraft([])}>
                    <X aria-hidden /> Clear outline
                  </Button>
                </>
              ) : (
                <Button onClick={startCreate}>
                  <Plus aria-hidden /> New zone
                </Button>
              )}
            </div>
          </CardHeader>
          <CardContent>
            <ZoneMap
              zones={mapZones}
              selectedId={drawing ? undefined : selectedId}
              draft={drawing ? draft : undefined}
              onAddPoint={drawing ? (p) => setDraft((d) => [...d, p]) : undefined}
              onSelectZone={setSelectedId}
            />
            <ul className="mt-3 flex flex-wrap gap-4 text-xs text-muted-foreground" aria-label="Map legend">
              <li className="flex items-center gap-1.5"><span aria-hidden className="size-3 rounded-sm border-2 border-brand bg-brand/15" /> Active zone</li>
              <li className="flex items-center gap-1.5"><span aria-hidden className="size-3 rounded-sm border-2 border-dashed border-muted-foreground" /> Inactive zone</li>
              <li className="flex items-center gap-1.5"><span aria-hidden className="size-3 rounded-sm border-2 border-tape bg-tape/25" /> Selected or being drawn</li>
            </ul>
          </CardContent>
        </Card>

        <div className="grid content-start gap-6">
          {drawing ? (
            <Card>
              <CardHeader>
                <CardTitle>{mode.kind === "create" ? "New zone" : "Edit zone"}</CardTitle>
                <CardDescription>
                  {mode.kind === "create"
                    ? "Name the zone and set the rule it starts with. You can add later rules at any time."
                    : "Rename the zone or redraw its outline. Pricing rules are changed below the zone details."}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <ZoneEditor
                  key={mode.kind === "edit" ? mode.zone.id : "new"}
                  zone={mode.kind === "edit" ? mode.zone : undefined}
                  points={draft}
                  onCancel={stopDrawing}
                  onSaved={(zone) => {
                    setNotice(mode.kind === "create" ? `${zone.name} created.` : `${zone.name} saved.`);
                    setSelectedId(zone.id);
                    stopDrawing();
                  }}
                />
              </CardContent>
            </Card>
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>All zones</CardTitle>
              </CardHeader>
              <CardContent className="grid gap-2">
                {notice && <p role="status" className="text-sm text-muted-foreground">{notice}</p>}
                {zones.isPending ? (
                  <p className="text-sm text-muted-foreground">Loading zones…</p>
                ) : zones.error ? (
                  <FormError message={zones.error instanceof ApiError ? zones.error.message : "Could not load the zones."} />
                ) : zones.data?.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No zones yet. Draw the first one with “New zone”.</p>
                ) : (
                  <ul className="grid max-h-80 gap-1 overflow-y-auto pr-1">
                    {zones.data?.map((zone) => (
                      <li key={zone.id}>
                        <button
                          type="button"
                          onClick={() => setSelectedId(zone.id)}
                          aria-current={zone.id === selectedId ? "true" : undefined}
                          className={
                            "flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2 text-left transition-colors " +
                            (zone.id === selectedId ? "bg-accent text-accent-foreground" : "hover:bg-muted")
                          }
                        >
                          <span className="min-w-0">
                            <span className="block truncate font-medium">{zone.name}</span>
                            <span className="block truncate text-xs text-muted-foreground">{ruleSummary(zone)}</span>
                          </span>
                          {!zone.active && <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">Inactive</span>}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          )}

          {!drawing && selected && (
            <Card>
              <CardHeader className="border-b">
                <CardTitle className="text-lg">{selected.name}</CardTitle>
                <CardDescription>
                  {selected.active ? "Active: offered for quotes." : "Inactive: not offered for quotes."} {ruleSummary(selected)}
                </CardDescription>
                <div className="mt-2 flex flex-wrap gap-2">
                  <Button variant="outline" onClick={() => startEdit(selected)}>Edit name or outline</Button>
                  <Button
                    variant={selected.active ? "destructive" : "outline"}
                    disabled={toggle.isPending}
                    onClick={() => {
                      const ask = selected.active
                        ? `Deactivate ${selected.name}? Customers will not be able to book deliveries to or from it.`
                        : `Activate ${selected.name} again?`;
                      if (window.confirm(ask)) toggle.mutate(selected);
                    }}
                  >
                    {selected.active ? "Deactivate" : "Activate"}
                  </Button>
                </div>
                <FormError message={toggle.error instanceof ApiError ? toggle.error.message : toggle.error ? "Could not change the zone." : undefined} />
              </CardHeader>
              <CardContent>
                <PricingRules key={selected.id} zone={selected} />
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

function ruleSummary(zone: Zone): string {
  const rule = zone.currentRule;
  if (!rule) return "No pricing rule in force: default rates apply.";
  const label = STRATEGY_LABEL[rule.strategy] ?? rule.strategy;
  return rule.strategy === "FLAT_RATE"
    ? `${label}: ${formatLkr(rule.baseFee)} per parcel.`
    : `${label}: ${formatLkr(rule.baseFee)} + ${formatLkr(rule.perKgFee)}/kg.`;
}
