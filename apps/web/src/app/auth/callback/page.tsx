"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Brand } from "@/components/brand";
import { homeFor } from "@/lib/roles";
import { useMe } from "@/lib/session";

/** Google sign-in lands here with fresh cookies; /me tells us which dashboard to open. */
export default function AuthCallbackPage() {
  const router = useRouter();
  const me = useMe();

  useEffect(() => {
    if (me.data) router.replace(homeFor(me.data.roles));
    if (me.error) router.replace("/login?error=google");
  }, [me.data, me.error, router]);

  return (
    <div className="grid min-h-dvh place-items-center px-5">
      <div className="grid justify-items-center gap-4 text-center">
        <Brand />
        <p className="text-muted-foreground" aria-live="polite">
          Signing you in…
        </p>
      </div>
    </div>
  );
}
