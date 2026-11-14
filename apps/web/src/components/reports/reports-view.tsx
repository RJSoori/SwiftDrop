"use client";

import { useQuery } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { FormError } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, ApiError } from "@/lib/api";
import { addDays, todayInColombo } from "@/lib/dates";
import {
  dayLabel,
  defaultRange,
  formatRate,
  niceMax,
  rangeProblem,
  rangeQuery,
  type DailyStats,
  type DateRange,
  type ReportSummary,
} from "@/lib/reports";

/*
 * Series colours, validated for colour-blind separation and contrast against the card surface in both modes
 * (dataviz validate_palette.js: light #2f3bd1/#d4720b on #ffffff, dark #6b77f5/#d9731a on #171b36).
 */
const SERIES_VARS =
  "[--series-delivered:#2f3bd1] [--series-failed:#d4720b] dark:[--series-delivered:#6b77f5] dark:[--series-failed:#d9731a]";

const PRESETS = [7, 30, 90] as const;

/** UC-11: deliveries per day and success / failure rates. Used by the admin and dispatcher Reports pages. */
export function ReportsView() {
  const [range, setRange] = useState<DateRange>(() => defaultRange());
  const problem = rangeProblem(range);
  const query = rangeQuery(range);

  const summary = useQuery({
    queryKey: ["reports", "summary", query],
    queryFn: () => api<ReportSummary>(`/reports/summary?${query}`),
    enabled: !problem,
  });
  const daily = useQuery({
    queryKey: ["reports", "daily", query],
    queryFn: () => api<DailyStats[]>(`/reports/daily?${query}`),
    enabled: !problem,
  });
  const [download, setDownload] = useState<{ busy: boolean; error?: string }>({ busy: false });

  const error = summary.error ?? daily.error;
  const empty = daily.data?.every((d) => d.created + d.delivered + d.failed === 0);

  const onDownload = async () => {
    setDownload({ busy: true });
    try {
      await downloadCsv(query);
      setDownload({ busy: false });
    } catch {
      setDownload({ busy: false, error: "Could not download the CSV. Try again." });
    }
  };

  return (
    <div className={"mt-8 grid gap-6 " + SERIES_VARS}>
      {/* Filters: one row above everything they control. */}
      <div className="flex flex-wrap items-end gap-3 rounded-xl border bg-card p-4">
        <div className="grid gap-1.5">
          <Label htmlFor="report-from">From</Label>
          <Input id="report-from" type="date" className="h-9 w-40" value={range.from} max={range.to}
            onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="report-to">To</Label>
          <Input id="report-to" type="date" className="h-9 w-40" value={range.to} max={todayInColombo()}
            onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))} />
        </div>
        <div className="flex gap-1" role="group" aria-label="Quick ranges">
          {PRESETS.map((days) => {
            const today = todayInColombo();
            const active = range.to === today && range.from === addDays(today, -(days - 1));
            return (
              <Button key={days} variant={active ? "secondary" : "ghost"} aria-pressed={active}
                onClick={() => setRange({ from: addDays(today, -(days - 1)), to: today })}>
                {days} days
              </Button>
            );
          })}
        </div>
        <Button variant="outline" className="ml-auto" disabled={!!problem || download.busy} onClick={onDownload}>
          <Download aria-hidden /> {download.busy ? "Preparing…" : "Download CSV"}
        </Button>
      </div>

      <FormError message={problem ?? download.error ?? (error instanceof ApiError ? error.message : error ? "Could not load the report." : undefined)} />

      <dl className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Tile label="Parcels booked" value={summary.data?.created} />
        <Tile label="Delivered" value={summary.data?.delivered} swatch="var(--series-delivered)" />
        <Tile label="Failed" value={summary.data?.failed} swatch="var(--series-failed)" />
        <Tile
          label="Success rate"
          value={summary.data ? formatRate(summary.data.successRate) : undefined}
          note={summary.data ? `Failure rate ${formatRate(summary.data.failureRate)} of ${summary.data.completed.toLocaleString("en-LK")} completed` : undefined}
        />
      </dl>

      <section aria-labelledby="per-day" className="rounded-xl border bg-card p-4 sm:p-5">
        <div className="flex flex-wrap items-baseline justify-between gap-3">
          <div>
            <h2 id="per-day" className="text-base font-semibold">Completed deliveries per day</h2>
            <p className="text-sm text-muted-foreground">Delivered and failed, by the Sri Lankan day the courier finished.</p>
          </div>
          <ul className="flex gap-4 text-sm" aria-label="Legend">
            <li className="flex items-center gap-1.5"><span aria-hidden className="size-3 rounded-sm bg-[var(--series-delivered)]" /> Delivered</li>
            <li className="flex items-center gap-1.5"><span aria-hidden className="size-3 rounded-sm bg-[var(--series-failed)]" /> Failed</li>
          </ul>
        </div>
        {daily.isPending && !problem ? (
          <p className="py-16 text-center text-sm text-muted-foreground">Loading report…</p>
        ) : empty ? (
          <p className="py-16 text-center text-sm text-muted-foreground">No parcels were booked or delivered in this range.</p>
        ) : daily.data ? (
          <>
            <DailyChart days={daily.data} />
            <details className="mt-4">
              <summary className="cursor-pointer text-sm font-medium">Show as a table</summary>
              <div className="mt-3 max-h-96 overflow-auto rounded-lg border [&_td]:px-3 [&_th]:px-3">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Day</TableHead>
                      <TableHead className="text-right">Booked</TableHead>
                      <TableHead className="text-right">Delivered</TableHead>
                      <TableHead className="text-right">Failed</TableHead>
                      <TableHead className="text-right">Success rate</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {daily.data.map((d) => (
                      <TableRow key={d.day}>
                        <TableCell className="tabular whitespace-nowrap">{d.day}</TableCell>
                        <TableCell className="tabular text-right">{d.created}</TableCell>
                        <TableCell className="tabular text-right">{d.delivered}</TableCell>
                        <TableCell className="tabular text-right">{d.failed}</TableCell>
                        <TableCell className="tabular text-right">
                          {formatRate(d.delivered + d.failed === 0 ? null : (d.delivered * 100) / (d.delivered + d.failed))}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </details>
          </>
        ) : null}
      </section>
    </div>
  );
}

function Tile({ label, value, note, swatch }: { label: string; value?: number | string; note?: string; swatch?: string }) {
  return (
    <div className="rounded-xl border bg-card p-4">
      <dt className="flex items-center gap-1.5 text-sm text-muted-foreground">
        {swatch && <span aria-hidden className="size-2.5 rounded-sm" style={{ background: swatch }} />}
        {label}
      </dt>
      <dd className="tabular mt-1 text-3xl font-semibold">
        {value === undefined ? "–" : typeof value === "number" ? value.toLocaleString("en-LK") : value}
      </dd>
      {note && <dd className="mt-1 text-xs text-muted-foreground">{note}</dd>}
    </div>
  );
}

const HEIGHT = 240;
const PAD = { top: 12, right: 8, bottom: 28, left: 36 };

/** Stacked columns (delivered under failed) with a per-day hover tooltip. One y-axis: parcels. */
function DailyChart({ days }: { days: DailyStats[] }) {
  const wrapper = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);
  const [hover, setHover] = useState<number>();

  useEffect(() => {
    const el = wrapper.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(280, Math.floor(entry.contentRect.width))));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const max = niceMax(Math.max(...days.map((d) => d.delivered + d.failed)));
  const plotW = width - PAD.left - PAD.right;
  const plotH = HEIGHT - PAD.top - PAD.bottom;
  const slot = plotW / days.length;
  const barW = Math.max(1, Math.min(24, slot - 2)); // 2px surface gap between neighbours
  const y = (v: number) => PAD.top + plotH - (v / max) * plotH;
  const ticks = [0, max / 2, max];
  const labelEvery = Math.ceil(days.length / Math.max(1, Math.floor(plotW / 56)));
  const hovered = hover === undefined ? undefined : days[hover];

  return (
    <div ref={wrapper} className="relative mt-4" onMouseLeave={() => setHover(undefined)}>
      <svg width={width} height={HEIGHT} role="img" aria-label={`Completed deliveries per day from ${days[0].day} to ${days[days.length - 1].day}. The table below has every value.`}>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={PAD.left} x2={width - PAD.right} y1={y(t)} y2={y(t)} className="stroke-border" strokeWidth={1} />
            <text x={PAD.left - 6} y={y(t)} dy="0.32em" textAnchor="end" className="fill-muted-foreground text-[11px] tabular">
              {Number.isInteger(t) ? t.toLocaleString("en-LK") : t.toFixed(1)}
            </text>
          </g>
        ))}
        {days.map((d, i) => {
          const x = PAD.left + i * slot + (slot - barW) / 2;
          const deliveredTop = y(d.delivered);
          const failedTop = y(d.delivered + d.failed);
          const gap = d.delivered > 0 && d.failed > 0 ? 2 : 0;
          return (
            <g key={d.day} opacity={hover === undefined || hover === i ? 1 : 0.45}>
              {d.delivered > 0 && (
                <path d={column(x, deliveredTop, barW, y(0) - deliveredTop, d.failed === 0)} fill="var(--series-delivered)" />
              )}
              {d.failed > 0 && (
                <path d={column(x, failedTop, barW, Math.max(0, deliveredTop - failedTop - gap), true)} fill="var(--series-failed)" />
              )}
              {i % labelEvery === 0 && (
                <text x={PAD.left + i * slot + slot / 2} y={HEIGHT - 8} textAnchor="middle" className="fill-muted-foreground text-[11px]">
                  {dayLabel(d.day)}
                </text>
              )}
              {/* Hit target: the whole slot, taller and wider than the mark. */}
              <rect x={PAD.left + i * slot} y={PAD.top} width={slot} height={plotH} fill="transparent"
                onMouseEnter={() => setHover(i)} />
            </g>
          );
        })}
      </svg>
      {hovered && hover !== undefined && (
        <div
          role="status"
          className="pointer-events-none absolute top-0 z-10 w-44 rounded-lg border bg-popover px-3 py-2 text-sm shadow-md"
          style={{ left: Math.min(Math.max(0, PAD.left + hover * slot + slot / 2 - 88), width - 176) }}
        >
          <p className="font-medium">{dayLabel(hovered.day)}</p>
          <TooltipRow color="var(--series-delivered)" label="Delivered" value={hovered.delivered} />
          <TooltipRow color="var(--series-failed)" label="Failed" value={hovered.failed} />
          <p className="mt-1 flex justify-between text-muted-foreground"><span>Booked</span><span className="tabular">{hovered.created}</span></p>
        </div>
      )}
    </div>
  );
}

function TooltipRow({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <p className="flex items-center justify-between gap-3">
      <span className="flex items-center gap-1.5"><span aria-hidden className="size-2.5 rounded-sm" style={{ background: color }} />{label}</span>
      <span className="tabular">{value}</span>
    </p>
  );
}

/** A column with a 4px rounded top (when it is the top segment) and a square base. */
function column(x: number, top: number, w: number, h: number, roundTop: boolean): string {
  if (h <= 0) return "";
  const r = roundTop ? Math.min(4, w / 2, h) : 0;
  return `M${x},${top + h} V${top + r} Q${x},${top} ${x + r},${top} H${x + w - r} Q${x + w},${top} ${x + w},${top + r} V${top + h} Z`;
}

/** Downloads the server's CSV, refreshing the session once if the access token has expired. */
async function downloadCsv(query: string) {
  const url = `/api/v1/reports/daily.csv?${query}`;
  let res = await fetch(url, { credentials: "same-origin" });
  if (res.status === 401) {
    await api("/auth/refresh", { method: "POST" }).catch(() => undefined);
    res = await fetch(url, { credentials: "same-origin" });
  }
  if (!res.ok) throw new Error(`CSV download failed (${res.status})`);
  const blob = await res.blob();
  const filename = /filename="([^"]+)"/.exec(res.headers.get("Content-Disposition") ?? "")?.[1] ?? "swiftdrop-deliveries.csv";
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(href);
}
