"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { PageHeading } from "@/components/dashboard";
import { applyServerErrors, FormError, FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { api, type User } from "@/lib/api";
import { createStaffSchema, type CreateStaffInput } from "@/lib/schemas";

export default function StaffAccountsPage() {
  const [created, setCreated] = useState<User>();
  const form = useForm<CreateStaffInput>({ resolver: zodResolver(createStaffSchema), defaultValues: { role: "COURIER" } });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    setCreated(undefined);
    try {
      setCreated(await api<User>("/auth/admin/users", { method: "POST", json: values }));
      form.reset({ role: values.role, fullName: "", email: "", phone: "", password: "" });
    } catch (error) {
      applyServerErrors(form, error);
    }
  });

  return (
    <>
      <PageHeading
        title="Staff accounts"
        intro="Create logins for couriers and dispatchers. Share the password with them directly; they can log in straight away."
      />

      <form onSubmit={onSubmit} noValidate className="mt-8 grid max-w-xl gap-5">
        <FormError message={errors.root?.message} />
        {created && (
          <p role="status" className="rounded-md border border-emerald-600/30 bg-emerald-600/10 px-3 py-2 text-sm">
            Created {created.name} as a {created.roles[0].toLowerCase()}. They can log in with {created.email}.
          </p>
        )}

        <fieldset className="grid gap-2">
          <legend className="mb-2 text-sm font-medium">Role</legend>
          <div className="flex flex-wrap gap-3">
            {(["COURIER", "DISPATCHER"] as const).map((role) => (
              <Label
                key={role}
                className="cursor-pointer rounded-lg border px-4 py-2.5 has-checked:border-brand has-checked:bg-accent has-checked:text-accent-foreground"
              >
                <input type="radio" value={role} className="accent-brand" {...form.register("role")} />
                {role === "COURIER" ? "Courier" : "Dispatcher"}
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
        <Button type="submit" size="lg" className="h-11 justify-self-start px-5 text-base" disabled={isSubmitting}>
          {isSubmitting ? "Creating…" : "Create account"}
        </Button>
      </form>
    </>
  );
}
