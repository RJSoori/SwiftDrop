"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm, type FieldValues, type Path, type Resolver, type UseFormReturn } from "react-hook-form";
import { z } from "zod";
import { FormError, FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, ApiError } from "@/lib/api";
import { todayInColombo } from "@/lib/dates";
import {
  feeFor,
  formatLkr,
  outlineProblem,
  polygonFromPoints,
  pricingRuleSchema,
  STRATEGIES,
  STRATEGY_HELP,
  STRATEGY_LABEL,
  toRuleRequest,
  zoneNameSchema,
  type LatLng,
  type PricingRule,
  type PricingRuleInput,
  type Zone,
} from "@/lib/zones";

const DATE = new Intl.DateTimeFormat("en-LK", { dateStyle: "medium", timeZone: "Asia/Colombo" });

const ruleFields = pricingRuleSchema.omit({ validFrom: true });
const createSchema = z.object({ name: zoneNameSchema, pricingRule: ruleFields });
const editSchema = z.object({ name: zoneNameSchema });
type CreateInput = z.infer<typeof createSchema>;

const DEFAULT_RULE = { strategy: "WEIGHT_TIERED", baseFee: 400, perKgFee: 90, sizeMultiplier: 1.2 } as const;

/**
 * Name and outline of a new or existing zone. The outline is drawn on the map (owned by the page) and arrives as
 * {@code points}; a new zone is priced in the same request.
 */
export function ZoneEditor({ zone, points, onSaved, onCancel }: {
  zone?: Zone;
  points: LatLng[];
  onSaved: (zone: Zone) => void;
  onCancel: () => void;
}) {
  const queryClient = useQueryClient();
  const creating = !zone;
  const form = useForm<CreateInput>({
    // Editing validates only the name; the rule fields are not shown.
    resolver: (creating ? zodResolver(createSchema) : zodResolver(editSchema)) as Resolver<CreateInput>,
    defaultValues: { name: zone?.name ?? "", pricingRule: { ...DEFAULT_RULE } },
  });
  const [outlineError, setOutlineError] = useState<string>();
  const { errors, isSubmitting } = form.formState;
  const localOutlineProblem = outlineProblem(points);

  const onSubmit = form.handleSubmit(async (values) => {
    setOutlineError(undefined);
    if (localOutlineProblem) {
      setOutlineError(localOutlineProblem);
      return;
    }
    const body = {
      name: values.name.trim(),
      boundary: polygonFromPoints(points),
      ...(creating ? { pricingRule: values.pricingRule } : {}),
    };
    try {
      const saved = await api<Zone>(creating ? "/admin/zones" : `/admin/zones/${zone.id}`, {
        method: creating ? "POST" : "PUT",
        json: body,
      });
      await queryClient.invalidateQueries({ queryKey: ["zones"] });
      onSaved(saved);
    } catch (error) {
      setOutlineError(serverErrors(form, error, "boundary"));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <FormError message={errors.root?.message} />
      <FormField id="zone-name" label="Zone name" placeholder="e.g. Matara" error={errors.name?.message} {...form.register("name")} />

      <div className="grid gap-1.5">
        <p className="text-sm font-medium">Outline</p>
        <p className={"text-sm " + (outlineError ? "text-destructive" : "text-muted-foreground")} role={outlineError ? "alert" : undefined}>
          {outlineError ??
            (points.length === 0
              ? "Click on the map to place the corners of the zone."
              : `${points.length} point${points.length === 1 ? "" : "s"} placed. ${localOutlineProblem ?? "The outline is ready to save."}`)}
        </p>
      </div>

      {creating && <RuleFields form={form} prefix="pricingRule." />}

      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="lg" disabled={isSubmitting}>
          {isSubmitting ? "Saving…" : creating ? "Create zone" : "Save changes"}
        </Button>
        <Button type="button" size="lg" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

/** Rule history of a zone and the form for a new rule. Rules are never edited, only replaced or retired. */
export function PricingRules({ zone }: { zone: Zone }) {
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string>();
  const rules = useQuery({
    queryKey: ["zones", zone.id, "rules"],
    queryFn: () => api<PricingRule[]>(`/admin/zones/${zone.id}/pricing-rules`),
  });
  const form = useForm<PricingRuleInput>({
    resolver: zodResolver(pricingRuleSchema),
    defaultValues: zone.currentRule
      ? { strategy: zone.currentRule.strategy, baseFee: zone.currentRule.baseFee, perKgFee: zone.currentRule.perKgFee,
          sizeMultiplier: zone.currentRule.sizeMultiplier, validFrom: "" }
      : { ...DEFAULT_RULE, validFrom: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const retire = useMutation({
    mutationFn: (rule: PricingRule) => api(`/admin/zones/${zone.id}/pricing-rules/${rule.id}/deactivate`, { method: "POST" }),
    onSuccess: () => {
      setNotice("Rule retired. The previous rule applies again.");
      return queryClient.invalidateQueries({ queryKey: ["zones"] });
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setNotice(undefined);
    try {
      const rule = await api<PricingRule>(`/admin/zones/${zone.id}/pricing-rules`, { method: "POST", json: toRuleRequest(values) });
      setNotice(rule.current ? "New rule saved. Quotes use it from now on." : `New rule saved. It applies from ${DATE.format(new Date(rule.validFrom))}.`);
      form.reset({ ...values, validFrom: "" });
      await queryClient.invalidateQueries({ queryKey: ["zones"] });
    } catch (error) {
      serverErrors(form, error);
    }
  });

  const now = Date.now();
  return (
    <div className="grid gap-6">
      <section aria-labelledby="rule-history" className="grid gap-3">
        <h3 id="rule-history" className="text-sm font-semibold">Rule history</h3>
        {notice && <p role="status" className="text-sm text-muted-foreground">{notice}</p>}
        <FormError message={retire.error instanceof ApiError ? retire.error.message : retire.error ? "Could not retire the rule." : undefined} />
        {rules.isPending ? (
          <p className="text-sm text-muted-foreground">Loading rules…</p>
        ) : rules.error ? (
          <FormError message="Could not load the rules." />
        ) : rules.data?.length === 0 ? (
          <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
            No rules yet. Quotes in this zone use the default rates until you add one.
          </p>
        ) : (
          <div className="overflow-x-auto rounded-lg border [&_td]:px-3 [&_th]:px-3">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>From</TableHead>
                  <TableHead>Strategy</TableHead>
                  <TableHead className="text-right">Base</TableHead>
                  <TableHead className="text-right">Per kg</TableHead>
                  <TableHead className="text-right">Large ×</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead><span className="sr-only">Actions</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rules.data?.map((rule) => {
                  const status = !rule.active ? "Retired" : rule.current ? "In force" : Date.parse(rule.validFrom) > now ? "Scheduled" : "Replaced";
                  return (
                    <TableRow key={rule.id} className={rule.active ? undefined : "text-muted-foreground"}>
                      <TableCell className="tabular whitespace-nowrap">{DATE.format(new Date(rule.validFrom))}</TableCell>
                      <TableCell>{STRATEGY_LABEL[rule.strategy] ?? rule.strategy}</TableCell>
                      <TableCell className="tabular text-right">{formatLkr(rule.baseFee)}</TableCell>
                      <TableCell className="tabular text-right">{formatLkr(rule.perKgFee)}</TableCell>
                      <TableCell className="tabular text-right">{rule.sizeMultiplier.toFixed(2)}</TableCell>
                      <TableCell>
                        <span className={"rounded-full px-2 py-0.5 text-xs font-medium " + statusClass(status)}>{status}</span>
                      </TableCell>
                      <TableCell className="text-right">
                        {rule.active && (
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={retire.isPending}
                            onClick={() => {
                              if (window.confirm("Retire this rule? It stays in the history but no longer prices quotes.")) retire.mutate(rule);
                            }}
                          >
                            Retire
                          </Button>
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

      <section aria-labelledby="new-rule" className="grid gap-4">
        <div>
          <h3 id="new-rule" className="text-sm font-semibold">New pricing rule</h3>
          <p className="text-sm text-muted-foreground">Fees are in LKR. A rule starting today applies straight away.</p>
        </div>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormError message={errors.root?.message} />
          <RuleFields form={form} prefix="" />
          <FormField
            id="rule-valid-from"
            label="Applies from"
            type="date"
            min={todayInColombo()}
            hint="Leave empty to start today."
            error={errors.validFrom?.message}
            {...form.register("validFrom")}
          />
          <Button type="submit" size="lg" className="justify-self-start" disabled={isSubmitting}>
            {isSubmitting ? "Saving…" : "Save rule"}
          </Button>
        </form>
      </section>
    </div>
  );
}

/** Strategy, fees and multiplier, with a live example fee. Shared by the new-zone and new-rule forms. */
function RuleFields<T extends FieldValues>({ form, prefix }: { form: UseFormReturn<T>; prefix: "" | "pricingRule." }) {
  const name = (field: string) => `${prefix}${field}` as Path<T>;
  const error = (field: string) => {
    const errors = (prefix ? (form.formState.errors as Record<string, unknown>).pricingRule : form.formState.errors) as
      | Record<string, { message?: string } | undefined>
      | undefined;
    return errors?.[field]?.message;
  };
  const strategy = form.watch(name("strategy")) as PricingRule["strategy"];
  const rule = {
    strategy,
    baseFee: Number(form.watch(name("baseFee"))),
    perKgFee: Number(form.watch(name("perKgFee"))),
    sizeMultiplier: Number(form.watch(name("sizeMultiplier"))),
  };
  const valid = [rule.baseFee, rule.perKgFee, rule.sizeMultiplier].every((n) => Number.isFinite(n) && n >= 0);
  const id = (field: string) => `${prefix.replace(".", "-")}${field}`;

  return (
    <div className="grid gap-4">
      <div className="grid gap-1.5">
        <Label htmlFor={id("strategy")}>Pricing strategy</Label>
        <select
          id={id("strategy")}
          className="h-10 rounded-lg border border-input bg-background px-3 text-sm"
          aria-describedby={`${id("strategy")}-hint`}
          {...form.register(name("strategy"))}
        >
          {STRATEGIES.map((s) => (
            <option key={s} value={s}>{STRATEGY_LABEL[s]}</option>
          ))}
        </select>
        <p id={`${id("strategy")}-hint`} className={"text-sm " + (error("strategy") ? "text-destructive" : "text-muted-foreground")}>
          {error("strategy") ?? STRATEGY_HELP[strategy] ?? ""}
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <FormField id={id("baseFee")} label="Base fee (LKR)" type="number" inputMode="decimal" min={0} step="0.01"
          error={error("baseFee")} {...form.register(name("baseFee"), { valueAsNumber: true })} />
        <FormField id={id("perKgFee")} label="Per kg (LKR)" type="number" inputMode="decimal" min={0} step="0.01"
          error={error("perKgFee")} {...form.register(name("perKgFee"), { valueAsNumber: true })} />
        <FormField id={id("sizeMultiplier")} label="Large parcel ×" type="number" inputMode="decimal" min={0.5} max={5} step="0.05"
          error={error("sizeMultiplier")} {...form.register(name("sizeMultiplier"), { valueAsNumber: true })} />
      </div>
      {valid && strategy && (
        <p className="rounded-lg bg-muted px-3 py-2 text-sm">
          A 2 kg parcel costs <strong className="tabular">{formatLkr(feeFor(rule, 2))}</strong>, or{" "}
          <strong className="tabular">{formatLkr(feeFor(rule, 2, "LARGE"))}</strong> if it is large.
        </p>
      )}
    </div>
  );
}

/** Puts field errors on the form and returns the message for {@code outsideField} (not a form input), if any. */
function serverErrors<T extends FieldValues>(form: UseFormReturn<T>, error: unknown, outsideField?: string): string | undefined {
  if (!(error instanceof ApiError)) {
    form.setError("root" as Path<T>, { message: "Could not reach SwiftDrop. Check your connection and try again." });
    return undefined;
  }
  let outside: string | undefined;
  const entries = Object.entries(error.fieldErrors);
  for (const [field, message] of entries) {
    if (field === outsideField) outside = message;
    else form.setError(field as Path<T>, { message });
  }
  if (entries.length === 0) form.setError("root" as Path<T>, { message: error.message });
  return outside;
}

function statusClass(status: string) {
  switch (status) {
    case "In force":
      return "bg-emerald-600/10 text-emerald-700 dark:text-emerald-300";
    case "Scheduled":
      return "bg-accent text-accent-foreground";
    default:
      return "bg-muted text-muted-foreground";
  }
}
