"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { api, type User } from "./api";

export function useMe() {
  return useQuery({ queryKey: ["me"], queryFn: () => api<User>("/auth/me") });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return async () => {
    // Even if the server call fails, leave the dashboard: the cookies are HttpOnly so we cannot clear them here.
    await api("/auth/logout", { method: "POST" }).catch(() => undefined);
    queryClient.clear();
    router.replace("/login");
  };
}
