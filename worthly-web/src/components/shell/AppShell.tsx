"use client";

import { usePathname } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { Sidebar } from "@/components/shell/Sidebar";
import { TopBar } from "@/components/shell/TopBar";
import { ApiError, apiGet, apiSend } from "@/lib/api";
import { AppDataContext } from "@/lib/app-data";
import { formatInstant } from "@/lib/period";
import { readPrivacy, writePrivacy } from "@/lib/privacy";
import type { Account, Category, Connection, Notification, Owner, TransactionPage } from "@/lib/types";

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [owner, setOwner] = useState<Owner | null>(null);
  const [connections, setConnections] = useState<Connection[]>([]);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [hasTransactions, setHasTransactions] = useState(false);
  const [privacy, setPrivacyState] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const setPrivacy = useCallback((value: boolean) => {
    setPrivacyState(value);
    writePrivacy(value);
  }, []);

  const refresh = useCallback(async () => {
    const nextOwner = await apiGet<Owner>("/me");
    const [nextConnections, nextNotifications, nextCategories, nextAccounts, nextTx] = await Promise.all([
      apiGet<Connection[]>("/connections"),
      apiGet<Notification[]>("/notifications"),
      apiGet<Category[]>("/categories"),
      apiGet<Account[]>("/accounts"),
      apiGet<TransactionPage>("/transactions?size=1"),
    ]);
    setOwner(nextOwner);
    setConnections(nextConnections);
    setNotifications(nextNotifications);
    setCategories(nextCategories);
    setAccounts(nextAccounts);
    setHasTransactions(nextTx.total > 0);
    setError(null);
  }, []);

  useEffect(() => {
    setPrivacyState(readPrivacy());
    void refresh()
      .catch((err: unknown) => {
        if (err instanceof ApiError && err.status === 401) {
          return;
        }
        setError("Unable to load Worthly.");
      })
      .finally(() => setLoading(false));
  }, [refresh]);

  const syncAll = useCallback(async () => {
    const runnable = connections.filter((connection) => connection.status === "ACTIVE" || connection.status === "ERROR");
    if (runnable.length === 0 || syncing) {
      return;
    }
    setSyncing(true);
    try {
      await Promise.all(runnable.map((connection) => apiSend("POST", `/connections/${connection.id}/sync`)));
      await refresh();
    } catch {
      setError("Sync could not be started for every connection.");
    } finally {
      setSyncing(false);
    }
  }, [connections, refresh, syncing]);

  const stamp = useMemo(() => {
    if (syncing) {
      return "Syncing connected banks and investments…";
    }
    if (!owner) {
      return "Loading…";
    }
    const latest = connections
      .map((connection) => connection.lastSuccessfulSyncAt)
      .filter((value): value is string => Boolean(value))
      .sort()
      .at(-1);
    if (!latest) {
      return "No successful sync yet · bank data is not real-time";
    }
    return `Last updated ${formatInstant(latest, owner.reportingTimezone)} · bank data is not real-time`;
  }, [connections, owner, syncing]);

  if (loading) {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", background: "var(--paper)" }}>
        <p className="muted">Loading…</p>
      </div>
    );
  }

  if (!owner) {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", background: "var(--paper)" }}>
        <p className="muted">{error ?? "Signing you in…"}</p>
      </div>
    );
  }

  return (
    <AppDataContext.Provider
      value={{
        owner,
        connections,
        notifications,
        categories,
        accounts,
        hasTransactions,
        privacy,
        syncing,
        stamp,
        error,
        setPrivacy,
        refresh,
        syncAll,
      }}
    >
      <div style={{ display: "flex", height: "100vh", overflow: "hidden", background: "var(--paper)", fontFamily: "var(--font-sans)" }}>
        <Sidebar />
        <div style={{ flex: 1, minWidth: 0, minHeight: 0, display: "flex", flexDirection: "column" }}>
          <TopBar pathname={pathname} />
          <div style={{ flex: 1, minHeight: 0, overflow: "auto", padding: "22px 30px 34px" }}>
            {error ? (
              <div
                style={{
                  background: "#FBF3E4",
                  border: "1px solid rgba(138,100,18,.3)",
                  borderRadius: 12,
                  padding: "13px 16px",
                  marginBottom: 16,
                  color: "#6E5A2E",
                }}
              >
                {error}
              </div>
            ) : null}
            {children}
          </div>
        </div>
      </div>
    </AppDataContext.Provider>
  );
}
