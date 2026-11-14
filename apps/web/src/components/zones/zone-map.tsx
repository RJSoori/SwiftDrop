"use client";

import "leaflet/dist/leaflet.css";
import type * as Leaflet from "leaflet";
import { useEffect, useRef, useState } from "react";
import { pointsFromPolygon, type GeoJsonPolygon, type LatLng } from "@/lib/zones";

export type MapZone = { id: string; name: string; boundary: GeoJsonPolygon | null; active: boolean };

type Props = {
  zones: MapZone[];
  selectedId?: string;
  /** The outline being drawn or edited; shown on top of the zones. */
  draft?: LatLng[];
  /** When set, clicks on the map add a point instead of selecting a zone. */
  onAddPoint?: (point: LatLng) => void;
  onSelectZone?: (id: string) => void;
};

const SRI_LANKA: Leaflet.LatLngBoundsExpression = [[5.85, 79.5], [9.9, 81.95]];

/**
 * Leaflet map of the delivery zones with click-to-draw. Leaflet touches window, so it is imported only in the
 * browser. Colours come from the theme tokens, so the map follows light and dark mode.
 */
export function ZoneMap({ zones, selectedId, draft, onAddPoint, onSelectZone }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const leaflet = useRef<typeof Leaflet | null>(null);
  const map = useRef<Leaflet.Map | null>(null);
  const layers = useRef<Leaflet.LayerGroup | null>(null);
  const handlers = useRef({ onAddPoint, onSelectZone });
  const [ready, setReady] = useState(false);
  const [tilesFailed, setTilesFailed] = useState(false);

  handlers.current = { onAddPoint, onSelectZone };

  useEffect(() => {
    let cancelled = false;
    void import("leaflet").then((L) => {
      if (cancelled || !container.current || map.current) return;
      leaflet.current = L;
      const m = L.map(container.current, { zoomSnap: 0.25, minZoom: 6, maxBounds: [[4.5, 78], [11, 83.5]] });
      m.fitBounds(SRI_LANKA);
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 18,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      })
        .on("tileerror", () => setTilesFailed(true))
        .addTo(m);
      m.on("click", (e: Leaflet.LeafletMouseEvent) => handlers.current.onAddPoint?.([e.latlng.lat, e.latlng.lng]));
      layers.current = L.layerGroup().addTo(m);
      map.current = m;
      setReady(true);
    });
    return () => {
      cancelled = true;
      map.current?.remove();
      map.current = null;
    };
  }, []);

  const drawing = !!onAddPoint;

  useEffect(() => {
    const L = leaflet.current;
    const group = layers.current;
    if (!ready || !L || !group) return;
    group.clearLayers();
    const colors = themeColors();

    for (const zone of zones) {
      const points = pointsFromPolygon(zone.boundary);
      if (points.length < 3) continue;
      const selected = zone.id === selectedId;
      const polygon = L.polygon(points, {
        color: selected ? colors.tape : zone.active ? colors.brand : colors.muted,
        weight: selected ? 3 : 1.5,
        dashArray: zone.active ? undefined : "6 4",
        fillOpacity: selected ? 0.25 : zone.active ? 0.12 : 0.04,
        // while drawing, clicks must reach the map so a point can be placed inside a zone's neighbour
        interactive: !drawing,
      }).bindTooltip(`${zone.name}${zone.active ? "" : " (inactive)"}`, { sticky: true });
      if (!drawing) polygon.on("click", () => handlers.current.onSelectZone?.(zone.id));
      polygon.addTo(group);
    }

    if (draft && draft.length > 0) {
      const outline = draft.length >= 3 ? L.polygon(draft, { color: colors.tape, weight: 2.5, fillOpacity: 0.2, interactive: false })
        : L.polyline(draft, { color: colors.tape, weight: 2.5, interactive: false });
      outline.addTo(group);
      draft.forEach((point, i) =>
        L.circleMarker(point, {
          radius: i === 0 ? 6 : 4,
          color: colors.tape,
          fillColor: colors.foreground,
          fillOpacity: 1,
          weight: 2,
          interactive: false,
        }).addTo(group),
      );
    }
  }, [ready, zones, selectedId, draft, drawing]);

  // Zoom to the selected zone so its edges can be checked.
  useEffect(() => {
    const L = leaflet.current;
    if (!ready || !L || !map.current || drawing) return;
    const zone = zones.find((z) => z.id === selectedId);
    const points = pointsFromPolygon(zone?.boundary);
    if (points.length >= 3) map.current.flyToBounds(L.latLngBounds(points), { padding: [24, 24], duration: 0.4 });
  }, [ready, selectedId, zones, drawing]);

  return (
    <div className="relative">
      <div
        ref={container}
        role="application"
        aria-label={drawing ? "Map of Sri Lanka. Click to add a point to the zone outline." : "Map of SwiftDrop delivery zones"}
        className={"h-[26rem] w-full overflow-hidden rounded-lg border bg-muted md:h-[32rem] " + (drawing ? "cursor-crosshair [&_.leaflet-grab]:cursor-crosshair" : "")}
      />
      {!ready && <p className="absolute inset-0 grid place-items-center text-sm text-muted-foreground">Loading map…</p>}
      {tilesFailed && (
        <p className="absolute bottom-2 left-2 z-[1000] rounded-md bg-card/90 px-2 py-1 text-xs text-muted-foreground shadow">
          Map tiles need an internet connection. Zones still show and can be drawn.
        </p>
      )}
    </div>
  );
}

function themeColors() {
  const css = getComputedStyle(document.documentElement);
  const read = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
  return {
    brand: read("--brand", "#2f3bd1"),
    tape: read("--tape", "#f2b200"),
    muted: read("--muted-foreground", "#565c7a"),
    foreground: read("--foreground", "#161a33"),
  };
}
