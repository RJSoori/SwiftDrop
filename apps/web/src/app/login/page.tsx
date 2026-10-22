"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { useForm } from "react-hook-form";
import { AuthShell } from "@/components/auth-shell";
import { applyServerErrors, FormError, FormField } from "@/components/form-field";
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

      <div className="flex items-center gap-3 text-sm text-muted-foreground">
        <span className="h-px flex-1 bg-border" />
        or
        <span className="h-px flex-1 bg-border" />
      </div>

      {/* Full-page navigation: the browser must follow the redirects to Google and back. */}
      <a
        href="/api/v1/auth/oauth2/authorize/google"
        className="inline-flex h-11 items-center justify-center gap-3 rounded-lg border border-input bg-card px-4 text-base font-medium hover:bg-muted"
      >
        <GoogleMark />
        Continue with Google
      </a>
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

function GoogleMark() {
  return (
    <svg aria-hidden viewBox="0 0 48 48" className="size-5">
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
      <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
    </svg>
  );
}
