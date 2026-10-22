"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { applyServerErrors, FormError, FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { api, type User } from "@/lib/api";
import { homeFor } from "@/lib/roles";
import { merchantSignupSchema, signupSchema, type MerchantSignupInput } from "@/lib/schemas";

/** Customer signup, or merchant signup when {@code merchant} is set (adds the business fields). */
export function SignupForm({ merchant = false }: { merchant?: boolean }) {
  const router = useRouter();
  // One form type for both: the customer schema never sees the business fields (they are not rendered).
  const schema = (merchant ? merchantSignupSchema : signupSchema) as typeof merchantSignupSchema;
  const form = useForm<MerchantSignupInput>({ resolver: zodResolver(schema) });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const user = await api<User>(merchant ? "/auth/signup/merchant" : "/auth/signup", { method: "POST", json: values });
      router.replace(homeFor(user.roles));
    } catch (error) {
      applyServerErrors(form, error);
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-5">
      <FormError message={errors.root?.message} />
      {merchant && (
        <>
          <FormField id="businessName" label="Business name" autoComplete="organization" error={errors.businessName?.message} {...form.register("businessName")} />
          <FormField
            id="registrationNo"
            label="Business registration number"
            hint="As shown on your business registration certificate, e.g. PV 12345."
            error={errors.registrationNo?.message}
            {...form.register("registrationNo")}
          />
        </>
      )}
      <FormField id="fullName" label="Full name" autoComplete="name" error={errors.fullName?.message} {...form.register("fullName")} />
      <FormField id="email" label="Email" type="email" autoComplete="email" error={errors.email?.message} {...form.register("email")} />
      <FormField
        id="phone"
        label="Mobile number"
        type="tel"
        autoComplete="tel"
        placeholder="0771234567"
        error={errors.phone?.message}
        {...form.register("phone")}
      />
      <FormField
        id="password"
        label="Password"
        type="password"
        autoComplete="new-password"
        hint="At least 8 characters, with a letter and a number."
        error={errors.password?.message}
        {...form.register("password")}
      />
      <Button type="submit" size="lg" className="h-11 text-base" disabled={isSubmitting}>
        {isSubmitting ? "Creating account…" : merchant ? "Apply for a merchant account" : "Create account"}
      </Button>
    </form>
  );
}
