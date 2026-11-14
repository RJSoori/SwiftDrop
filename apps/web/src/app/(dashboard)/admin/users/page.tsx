"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { PageHeading } from "@/components/dashboard";
import { applyServerErrors, FormError, FormField } from "@/components/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, ApiError, type AdminUser, type Page, type Role, type User, type UserStatus } from "@/lib/api";
import { createCourierProfile, VEHICLE_TYPES, type Zone } from "@/lib/dispatch";
import { ROLE_LABEL } from "@/lib/roles";
import { createStaffSchema } from "@/lib/schemas";
import { useMe } from "@/lib/session";

const dateFormat = new Intl.DateTimeFormat("en-LK", { dateStyle: "medium" });
const ROLES: Role[] = ["CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN"];
const PAGE_SIZE = 20;
const selectClass = "h-10 rounded-md border bg-background px-3 text-sm";

export default function UsersPage() {
  return (
    <>
      <PageHeading
        title="Users"
        intro="Find any account, disable it if it is misused, and create logins for couriers and dispatchers."
      />
      <UserList />
      <CreateStaff />
    </>
  );
}

// ---------- list, filter, disable / enable ----------

function UserList() {
  const queryClient = useQueryClient();
  const me = useMe();
  const [role, setRole] = useState<Role | "">("");
  const [status, setStatus] = useState<UserStatus | "">("");
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [confirmDisable, setConfirmDisable] = useState<string>();
  const [notice, setNotice] = useState<string>();

  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (role) params.set("role", role);
  if (status) params.set("status", status);
  if (q) params.set("q", q);

  const users = useQuery({
    queryKey: ["admin-users", role, status, q, page],
    queryFn: () => api<Page<AdminUser>>(`/auth/admin/users?${params}`),
    placeholderData: keepPreviousData,
  });

  const change = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "disable" | "enable" }) =>
      api<AdminUser>(`/auth/admin/users/${id}/${action}`, { method: "POST" }),
    onSuccess: (u, { action }) => {
      setConfirmDisable(undefined);
      setNotice(
        action === "disable"
          ? `${u.fullName} is disabled and has been signed out everywhere.`
          : `${u.fullName} can log in again.`,
      );
      return queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
  });

  const filter = (apply: () => void) => {
    apply();
    setPage(0);
  };
  const error = change.error ?? users.error;
  const data = users.data;

  return (
    <section className="mt-8 grid gap-4" aria-labelledby="users-heading">
      <h2 id="users-heading" className="sr-only">
        All accounts
      </h2>
      <form
        role="search"
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          filter(() => setQ(search.trim()));
        }}
      >
        <div className="grid gap-1.5">
          <Label htmlFor="filter-role">Role</Label>
          <select
            id="filter-role"
            className={selectClass}
            value={role}
            onChange={(e) => filter(() => setRole(e.target.value as Role | ""))}
          >
            <option value="">All roles</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {ROLE_LABEL[r]}
              </option>
            ))}
          </select>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="filter-status">Status</Label>
          <select
            id="filter-status"
            className={selectClass}
            value={status}
            onChange={(e) => filter(() => setStatus(e.target.value as UserStatus | ""))}
          >
            <option value="">Any status</option>
            <option value="ACTIVE">Active</option>
            <option value="DISABLED">Disabled</option>
          </select>
        </div>
        <div className="grid min-w-56 flex-1 gap-1.5">
          <Label htmlFor="filter-q">Name or email</Label>
          <Input id="filter-q" className="h-10" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <Button type="submit" size="lg" variant="outline" className="h-10">
          Search
        </Button>
      </form>

      <FormError message={error instanceof ApiError ? error.message : error ? "Could not load users." : undefined} />
      {notice && !error && (
        <p role="status" className="text-sm text-muted-foreground">
          {notice}
        </p>
      )}

      {users.isPending ? (
        <p className="text-muted-foreground">Loading users…</p>
      ) : data?.content.length === 0 ? (
        <div className="rounded-xl border border-dashed p-8 text-center text-muted-foreground">
          No accounts match these filters.
        </div>
      ) : (
        <>
          <div className="overflow-x-auto rounded-xl border bg-card [&_td]:px-4 [&_th]:px-4">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Mobile</TableHead>
                  <TableHead>Joined</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data?.content.map((u) => {
                  const busy = change.isPending && change.variables?.id === u.id;
                  const self = u.id === me.data?.id;
                  return (
                    <TableRow key={u.id} className={u.status === "DISABLED" ? "text-muted-foreground" : undefined}>
                      <TableCell>
                        <span className="block font-medium">{u.fullName}</span>
                        <span className="block text-muted-foreground">{u.email}</span>
                      </TableCell>
                      <TableCell>
                        {u.roles.map((r) => ROLE_LABEL[r]).join(", ")}
                        {u.merchantStatus && (
                          <span className="block text-xs text-muted-foreground">{u.merchantStatus.toLowerCase()}</span>
                        )}
                      </TableCell>
                      <TableCell className="tabular whitespace-nowrap">{u.phone ?? "-"}</TableCell>
                      <TableCell className="tabular whitespace-nowrap">{dateFormat.format(new Date(u.createdAt))}</TableCell>
                      <TableCell>
                        {u.status === "ACTIVE" ? (
                          <Badge variant="secondary">Active</Badge>
                        ) : (
                          <Badge variant="destructive">Disabled</Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-2">
                          {self ? (
                            <span className="text-sm text-muted-foreground">You</span>
                          ) : u.status === "DISABLED" ? (
                            <Button
                              size="lg"
                              variant="outline"
                              disabled={busy}
                              onClick={() => change.mutate({ id: u.id, action: "enable" })}
                            >
                              Enable
                            </Button>
                          ) : confirmDisable === u.id ? (
                            <>
                              <Button
                                size="lg"
                                variant="destructive"
                                disabled={busy}
                                onClick={() => change.mutate({ id: u.id, action: "disable" })}
                              >
                                {busy ? "Disabling…" : "Confirm disable"}
                              </Button>
                              <Button size="lg" variant="outline" disabled={busy} onClick={() => setConfirmDisable(undefined)}>
                                Cancel
                              </Button>
                            </>
                          ) : (
                            <Button size="lg" variant="outline" onClick={() => setConfirmDisable(u.id)}>
                              Disable
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
          {data && data.totalPages > 1 && (
            <nav aria-label="Pages" className="flex items-center gap-3 text-sm">
              <Button size="lg" variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <span className="tabular">
                Page {data.page + 1} of {data.totalPages} ({data.totalElements} accounts)
              </span>
              <Button size="lg" variant="outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
                Next
              </Button>
            </nav>
          )}
        </>
      )}
    </section>
  );
}

// ---------- create staff (courier profile goes to dispatch-service, M4) ----------

const staffFormSchema = createStaffSchema
  .extend({ vehicleType: z.string().optional(), homeZoneId: z.string().optional() })
  .refine((v) => v.role !== "COURIER" || !!v.vehicleType, {
    path: ["vehicleType"],
    message: "Choose the courier's vehicle",
  });
type StaffFormInput = z.infer<typeof staffFormSchema>;

function CreateStaff() {
  const queryClient = useQueryClient();
  const [created, setCreated] = useState<{ user: User; profileWarning?: string }>();
  const form = useForm<StaffFormInput>({
    resolver: zodResolver(staffFormSchema),
    defaultValues: { role: "COURIER", vehicleType: "", homeZoneId: "" },
  });
  const { errors, isSubmitting } = form.formState;
  const role = form.watch("role");

  // M8's zones; if the endpoint is not there yet the picker is simply hidden.
  const zones = useQuery({ queryKey: ["zones"], queryFn: () => api<Zone[]>("/zones"), retry: false });

  const onSubmit = form.handleSubmit(async ({ vehicleType, homeZoneId, ...staff }) => {
    setCreated(undefined);
    try {
      const user = await api<User>("/auth/admin/users", { method: "POST", json: staff });
      let profileWarning: string | undefined;
      if (staff.role === "COURIER" && vehicleType) {
        const profile = await createCourierProfile({
          userId: user.id,
          fullName: user.name,
          vehicleType,
          homeZoneId: homeZoneId || undefined,
        });
        if (!profile.saved) profileWarning = profile.reason;
      }
      setCreated({ user, profileWarning });
      form.reset({ role: staff.role, fullName: "", email: "", phone: "", password: "", vehicleType: "", homeZoneId: "" });
      await queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    } catch (error) {
      applyServerErrors(form, error);
    }
  });

  return (
    <section className="mt-12" aria-labelledby="staff-heading">
      <h2 id="staff-heading" className="text-lg font-semibold">
        Create a staff account
      </h2>
      <p className="mt-1 max-w-xl text-sm text-muted-foreground">
        Share the password with them directly; they can log in straight away.
      </p>

      <form onSubmit={onSubmit} noValidate className="mt-6 grid max-w-xl gap-5">
        <FormError message={errors.root?.message} />
        {created && (
          <p role="status" className="rounded-md border border-emerald-600/30 bg-emerald-600/10 px-3 py-2 text-sm">
            Created {created.user.name} as a {created.user.roles[0].toLowerCase()}. They can log in with{" "}
            {created.user.email}.
            {created.profileWarning && (
              <span className="mt-1 block text-amber-800 dark:text-amber-300">
                The courier profile was not saved because {created.profileWarning}. Add the vehicle and home zone in the
                dispatcher screens once it is available.
              </span>
            )}
          </p>
        )}

        <fieldset className="grid gap-2">
          <legend className="mb-2 text-sm font-medium">Role</legend>
          <div className="flex flex-wrap gap-3">
            {(["COURIER", "DISPATCHER"] as const).map((r) => (
              <Label
                key={r}
                className="cursor-pointer rounded-lg border px-4 py-2.5 has-checked:border-brand has-checked:bg-accent has-checked:text-accent-foreground"
              >
                <input type="radio" value={r} className="accent-brand" {...form.register("role")} />
                {r === "COURIER" ? "Courier" : "Dispatcher"}
              </Label>
            ))}
          </div>
          {errors.role && <p className="text-sm text-destructive">{errors.role.message}</p>}
        </fieldset>

        <FormField id="fullName" label="Full name" autoComplete="off" error={errors.fullName?.message} {...form.register("fullName")} />
        <FormField id="email" label="Email" type="email" autoComplete="off" error={errors.email?.message} {...form.register("email")} />
        <FormField
          id="phone"
          label="Mobile number"
          type="tel"
          placeholder="0771234567"
          error={errors.phone?.message}
          {...form.register("phone")}
        />
        <FormField
          id="password"
          label="Initial password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters, with a letter and a number."
          error={errors.password?.message}
          {...form.register("password")}
        />

        {role === "COURIER" && (
          <>
            <div className="grid gap-1.5">
              <Label htmlFor="vehicleType">Vehicle</Label>
              <select
                id="vehicleType"
                className={selectClass}
                aria-invalid={!!errors.vehicleType}
                {...form.register("vehicleType")}
              >
                <option value="">Choose a vehicle</option>
                {VEHICLE_TYPES.map((v) => (
                  <option key={v.value} value={v.value}>
                    {v.label}
                  </option>
                ))}
              </select>
              {errors.vehicleType && <p className="text-sm text-destructive">{errors.vehicleType.message}</p>}
            </div>
            {zones.data && zones.data.length > 0 && (
              <div className="grid gap-1.5">
                <Label htmlFor="homeZoneId">Home zone</Label>
                <select id="homeZoneId" className={selectClass} {...form.register("homeZoneId")}>
                  <option value="">No home zone</option>
                  {zones.data.map((z) => (
                    <option key={z.id} value={z.id}>
                      {z.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </>
        )}

        <Button type="submit" size="lg" className="h-11 justify-self-start px-5 text-base" disabled={isSubmitting}>
          {isSubmitting ? "Creating…" : "Create account"}
        </Button>
      </form>
    </section>
  );
}
