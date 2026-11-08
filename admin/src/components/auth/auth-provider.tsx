"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { usePathname, useRouter } from "next/navigation";
import { ApiError, apiRequest, resetCsrfToken } from "@/lib/api/client";
import { authQueryKey, type Actor } from "@/lib/auth/types";
import { resetClientTrackingSession } from "@/lib/audit/track-event";
import { SessionBoundary } from "@/lib/auth/session";

type AuthContextValue = {
  actor: Actor | null;
  loading: boolean;
  error: Error | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => void;
};
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const [boundary] = useState(() => new SessionBoundary());
  const [epoch, setEpoch] = useState(0);
  const [suspended, setSuspended] = useState(false);
  const pathnameRef = useRef(pathname);
  useEffect(() => { pathnameRef.current = pathname; }, [pathname]);
  const isPublic = pathname === "/login";
  const me = useQuery({
    queryKey: authQueryKey(epoch),
    queryFn: ({ signal }) => apiRequest<Actor>("/auth/me", { signal }),
    enabled: !isPublic && !suspended,
    staleTime: 15_000,
    retry: false,
  });

  const clearBoundary = useCallback(async () => {
    setSuspended(true);
    resetClientTrackingSession();
    resetCsrfToken();
    const next = await boundary.reset(queryClient);
    if (boundary.isCurrent(next)) setEpoch(next);
    return next;
  }, [boundary, queryClient]);

  useEffect(() => {
    const onUnauthorized = () => {
      if (pathnameRef.current === "/login") return;
      void clearBoundary().then((next) => {
        if (boundary.isCurrent(next)) router.replace("/login");
      });
    };
    window.addEventListener("audit:unauthenticated", onUnauthorized);
    return () => window.removeEventListener("audit:unauthenticated", onUnauthorized);
  }, [boundary, clearBoundary, router]);

  const login = async (username: string, password: string) => {
    const next = await clearBoundary();
    try {
      const actor = await apiRequest<Actor>("/auth/login", { method: "POST", body: { username, password } });
      if (!boundary.isCurrent(next)) throw new Error("인증 상태가 변경되었습니다. 다시 로그인해 주세요.");
      queryClient.setQueryData(authQueryKey(next), actor);
      setSuspended(false);
      router.replace("/dashboard");
    } catch (error) {
      if (boundary.isCurrent(next)) setSuspended(false);
      throw error;
    }
  };

  const logout = async () => {
    const next = await clearBoundary();
    try {
      await apiRequest<void>("/auth/logout", { method: "POST" });
      if (boundary.isCurrent(next)) router.replace("/login");
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        router.replace("/login");
        return;
      }
      if (boundary.isCurrent(next)) setSuspended(false);
      throw error;
    }
  };

  return <AuthContext.Provider value={{
    actor: suspended ? null : me.data ?? null,
    loading: suspended || (!isPublic && me.isPending),
    error: me.error,
    login,
    logout,
    refresh: () => { void me.refetch(); },
  }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("AuthProvider is required");
  return context;
}
