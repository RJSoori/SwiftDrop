"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { useForm } from "react-hook-form";
import { AuthShell } from "@/components/auth-shell";
import { applyServerErrors, FormError, FormField } from "@/components/form-field";
import { GoogleButton } from "@/components/google-button";
import { Button } from "@/components/ui/button";
import { api, type User } from "@/lib/api";
import { homeFor } from "@/lib/roles";
import { loginSchema, type LoginInput } from "@/lib/schemas";

const OAUTH_ERRORS: Record<string, string> = {
  google: "Google sign-in did not complete. Try again, or log in with your email.",
  google_account: "That Google account can't be used here. Its email must be verified and the account active.",
};

function LoginForm() {
  const router = useRouter();
  const oauthError = OAUTH_ERRORS[useSearchParams().get("error") ?? ""];
  const form = useForm<LoginInput>({ resolver: zodResolver(loginSchema) });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const user = await api<User>("/auth/login", { method: "POST", json: values });
      router.replace(homeFor(user.roles));
    } catch (error) {
      applyServerErrors(form, error);
    }
  });

  return (
    <div className="grid gap-6">
      <FormError message={errors.root?.message ?? oauthError} />
      <form onSubmit={onSubmit} noValidate className="grid gap-5">
        <FormField id="email" label="Email" type="email" autoComplete="email" error={errors.email?.message} {...form.register("email")} />
        <FormField
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...form.register("password")}
        />
        <Button type="submit" size="lg" className="h-11 text-base" disabled={isSubmitting}>
          {isSubmitting ? "Logging in…" : "Log in"}
        </Button>
      </form>

      <GoogleButton label="Continue with Google" />
    </div>
  );
}

export default function LoginPage() {
  return (
    <AuthShell
      title="Log in"
      intro="Customers, merchants and SwiftDrop staff all log in here."
      footer={
        <>
          New to SwiftDrop?{" "}
          <Link href="/signup" className="font-medium text-brand underline-offset-4 hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <Suspense>
        <LoginForm />
      </Suspense>
    </AuthShell>
  );
}
