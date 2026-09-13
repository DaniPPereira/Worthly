"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Primitives";
import { apiGet, apiSend } from "@/lib/api";
import { connectionLabel, statusTone, useAppData } from "@/lib/app-data";
import { formatInstant } from "@/lib/period";
import type { BankChoice, Connection, SyncRun, SyncRunPage } from "@/lib/types";

type LogRow = SyncRun & { provider: string };

export function ConnectionsPage() {
  const { connections, refresh, syncing, owner } = useAppData();
  const searchParams = useSearchParams();
  const [banks, setBanks] = useState<BankChoice[]>([]);
  const [log, setLog] = useState<LogRow[]>([]);
  const [handoff, setHandoff] = useState<Connection | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirmPurge, setConfirmPurge] = useState<Connection | null>(null);
  const result = searchParams.get("status");

  useEffect(() => {
    void apiGet<BankChoice[]>("/connections/banks?country=PT").then(setBanks).catch(() => setBanks([]));
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.all(
      connections.map(async (connection) => {
        try {
          const page = await apiGet<SyncRunPage>(`/connections/${connection.id}/sync-runs?size=5`);
          return page.items.map((item) => ({ ...item, provider: connectionLabel(connection) }));
        } catch {
          return [] as LogRow[];
        }
      }),
    ).then((pages) => {
      if (cancelled) {
        return;
      }
      setLog(pages.flat().sort((a, b) => b.startedAt.localeCompare(a.startedAt)).slice(0, 8));
    });
    return () => {
      cancelled = true;
    };
  }, [connections]);

  async function authorize(name: string, country: string) {
    const start = await apiSend<{ url: string }>("POST", "/connections/enable-banking/authorize", {
      name,
      country,
      returnClient: "WEB",
    });
    if (start?.url) {
      window.location.assign(start.url);
    }
  }

  async function syncOne(connection: Connection) {
    setBusyId(connection.id);
    try {
      await apiSend("POST", `/connections/${connection.id}/sync`);
      await refresh();
    } finally {
      setBusyId(null);
    }
  }

  async function disconnect(connection: Connection) {
    setBusyId(connection.id);
    try {
      await apiSend("DELETE", `/connections/${connection.id}`);
      await refresh();
    } finally {
      setBusyId(null);
    }
  }

  async function purge(connection: Connection) {
    setBusyId(connection.id);
    try {
      await apiSend("POST", `/connections/${connection.id}/purge`, { confirm: true });
      await refresh();
      setConfirmPurge(null);
    } finally {
      setBusyId(null);
    }
  }

  const unusedBanks = useMemo(() => {
    const names = new Set(connections.map((item) => item.institutionName).filter(Boolean));
    return banks.filter((bank) => !names.has(bank.name));
  }, [banks, connections]);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      {result === "ok" ? (
        <div style={{ background: "rgba(20,101,74,.12)", border: "1px solid rgba(20,101,74,.3)", borderRadius: 12, padding: "13px 16px", color: "var(--gain)" }}>
          Bank connection completed. Worthly validated the returning state before storing accounts.
        </div>
      ) : null}
      {result === "error" ? (
        <div style={{ background: "#FBF3E4", border: "1px solid rgba(138,100,18,.3)", borderRadius: 12, padding: "13px 16px", color: "#6E5A2E" }}>
          Bank authorization did not finish. You can try again — Worthly never sees your bank password.
        </div>
      ) : null}
      {connections.length === 0 ? (
        <EmptyState title="No providers yet">Connect Santander Portugal or Revolut through Enable Banking. Trading 212 is configured with a server-side read-only key.</EmptyState>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 16 }}>
          {connections.map((connection) => (
            <ConnectionCard
              key={connection.id}
              connection={connection}
              timezone={owner.reportingTimezone}
              busy={busyId === connection.id || syncing}
              onSync={() => void syncOne(connection)}
              onDisconnect={() => void disconnect(connection)}
              onPurge={() => setConfirmPurge(connection)}
              onReauth={() => setHandoff(connection)}
            />
          ))}
        </div>
      )}
      {unusedBanks.length > 0 ? (
        <div className="card" style={{ padding: 20 }}>
          <div className="label">Connect a bank</div>
          <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
            {unusedBanks.map((bank) => (
              <button key={`${bank.country}-${bank.name}`} type="button" className="btn btn-primary" onClick={() => void authorize(bank.name, bank.country)}>
                Connect {bank.name}
              </button>
            ))}
          </div>
          <p className="muted" style={{ marginTop: 12 }}>Opens your bank in this browser. You confirm there — Worthly never sees your credentials.</p>
        </div>
      ) : null}
      <div className="card" style={{ padding: "20px 20px 8px" }}>
        <div className="label">Synchronization log</div>
        {log.length === 0 ? (
          <p className="muted" style={{ marginTop: 12 }}>No sync runs yet.</p>
        ) : (
          log.map((row) => (
            <div
              key={row.id}
              style={{
                display: "grid",
                gridTemplateColumns: "150px 190px 1fr 120px",
                gap: 14,
                padding: "11px 0",
                borderTop: "1px solid rgba(19,26,25,.06)",
                marginTop: 8,
                alignItems: "center",
              }}
            >
              <span className="mono" style={{ fontSize: 12, color: "var(--faint)" }}>
                {formatInstant(row.startedAt, owner.reportingTimezone)}
              </span>
              <span style={{ fontWeight: 500, fontSize: 12.5 }}>{row.provider}</span>
              <span style={{ fontSize: 12, color: "#5E6A67" }}>{detailFor(row)}</span>
              <span className="mono" style={{ textAlign: "right", fontSize: 9.5, letterSpacing: ".08em", textTransform: "uppercase", color: outcomeColor(row.status) }}>
                {row.status}
              </span>
            </div>
          ))
        )}
      </div>
      <div className="muted">
        Disconnecting stops future sync. Purging deletes locally stored provider data for that connection. Your own categories, notes and transfer matches are kept.
      </div>
      {handoff ? (
        <div style={{ position: "fixed", inset: 0, zIndex: 80, background: "rgba(19,26,25,.46)", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <div style={{ width: 420, background: "var(--paper)", borderRadius: 18, padding: 26, textAlign: "center" }}>
            <div style={{ width: 46, height: 46, borderRadius: 13, background: "var(--pine)", margin: "0 auto", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#F4F1EA" strokeWidth="1.8" strokeLinecap="round">
                <path d="M14 4h6v6" />
                <path d="M20 4 11 13" />
                <path d="M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4" />
              </svg>
            </div>
            <div style={{ fontWeight: 600, fontSize: 16, marginTop: 15 }}>Opening {connectionLabel(handoff)} in this browser</div>
            <div className="muted" style={{ marginTop: 8 }}>
              You&apos;ll confirm access with your bank, then be returned to Worthly through a verified link. Worthly validates the returning state before completing the connection.
            </div>
            <div style={{ display: "flex", gap: 9, marginTop: 20 }}>
              <button type="button" className="btn btn-ghost" style={{ flex: 1, height: 44 }} onClick={() => setHandoff(null)}>
                Not now
              </button>
              <button
                type="button"
                className="btn btn-primary"
                style={{ flex: 1, height: 44 }}
                onClick={() => void authorize(handoff.institutionName ?? "", handoff.institutionCountry ?? "PT")}
              >
                Continue
              </button>
            </div>
          </div>
        </div>
      ) : null}
      {confirmPurge ? (
        <div style={{ position: "fixed", inset: 0, zIndex: 80, background: "rgba(19,26,25,.46)", display: "flex", alignItems: "center", justifyContent: "center" }}>
          <div style={{ width: 420, background: "var(--paper)", borderRadius: 18, padding: 26 }}>
            <div style={{ fontWeight: 600, fontSize: 16 }}>Delete local provider data?</div>
            <p className="muted">This removes imported data for {connectionLabel(confirmPurge)}. Categories and notes you wrote stay. Type-in confirmation is the API `confirm` flag.</p>
            <div style={{ display: "flex", gap: 9, marginTop: 20 }}>
              <button type="button" className="btn btn-ghost" style={{ flex: 1 }} onClick={() => setConfirmPurge(null)}>
                Cancel
              </button>
              <button type="button" className="btn btn-danger" style={{ flex: 1 }} onClick={() => void purge(confirmPurge)}>
                Delete data
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function ConnectionCard({
  connection,
  timezone,
  busy,
  onSync,
  onDisconnect,
  onPurge,
  onReauth,
}: {
  connection: Connection;
  timezone: string;
  busy: boolean;
  onSync: () => void;
  onDisconnect: () => void;
  onPurge: () => void;
  onReauth: () => void;
}) {
  const tone = statusTone(connection.status);
  const name = connectionLabel(connection);
  const bank = connection.provider !== "TRADING_212";
  const needsAuth = connection.status === "REAUTH_REQUIRED";
  const healthy = connection.status === "ACTIVE";
  const initial = name.slice(0, 1).toUpperCase();
  return (
    <div className="card" style={{ padding: 20, display: "flex", flexDirection: "column", borderColor: needsAuth ? "rgba(138,100,18,.35)" : undefined }}>
      <div style={{ display: "flex", alignItems: "center", gap: 11 }}>
        <div style={{ width: 30, height: 30, borderRadius: 9, background: "rgba(19,26,25,.08)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 600, fontSize: 13 }}>
          {initial}
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 600, fontSize: 14 }}>{name}</div>
          <div style={{ fontSize: 11, color: "var(--faint)", marginTop: 1 }}>
            {bank ? "Enable Banking · AIS" : "Public API · read-only key"}
          </div>
        </div>
      </div>
      <span className="mono" style={{ alignSelf: "flex-start", marginTop: 14, fontSize: 9.5, letterSpacing: ".08em", textTransform: "uppercase", color: tone.fg, background: tone.bg, borderRadius: 5, padding: "4px 7px" }}>
        {tone.label}
      </span>
      <div style={{ marginTop: 16, display: "flex", flexDirection: "column", gap: 8 }}>
        <Row k="Last successful sync" v={formatInstant(connection.lastSuccessfulSyncAt, timezone)} />
        <Row
          k={consentLabel(connection)}
          v={connection.consentExpiresAt ? formatInstant(connection.consentExpiresAt, timezone) : bank ? "—" : "Server secret"}
          warn={needsAuth || connection.status === "CONFIGURATION_REQUIRED"}
        />
        <Row k="Access" v="Read-only" />
      </div>
      <div style={{ marginTop: "auto", paddingTop: 18 }}>
        {needsAuth && bank ? (
          <>
            <button type="button" className="btn btn-primary" style={{ width: "100%", height: 42 }} onClick={onReauth} disabled={busy}>
              Reauthorize
            </button>
            <div style={{ fontSize: 11, lineHeight: 1.5, color: "#6E5A2E", marginTop: 9 }}>
              Opens {name} in this browser. You confirm there — Worthly never sees your credentials.
            </div>
          </>
        ) : connection.status === "CONFIGURATION_REQUIRED" ? (
          <p className="muted">Set WORTHLY_T212_API_KEY and WORTHLY_T212_API_SECRET on the server. Credentials are never stored on this connection.</p>
        ) : (
          <div style={{ display: "flex", gap: 8 }}>
            {healthy || connection.status === "ERROR" ? (
              <button type="button" className="btn btn-ghost" style={{ flex: 1, height: 42, background: "var(--paper)" }} onClick={onSync} disabled={busy}>
                Sync now
              </button>
            ) : null}
            {bank ? (
              <button type="button" className="btn btn-danger" style={{ flex: 1, height: 42 }} onClick={onDisconnect} disabled={busy}>
                Disconnect
              </button>
            ) : (
              <button type="button" className="btn btn-danger" style={{ flex: 1, height: 42 }} onClick={onPurge} disabled={busy}>
                Delete data
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function Row({ k, v, warn }: { k: string; v: string; warn?: boolean }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "#5E6A67" }}>
      <span>{k}</span>
      <span className="mono" style={{ fontWeight: 500, color: warn ? "var(--warn)" : "var(--ink)" }}>
        {v}
      </span>
    </div>
  );
}

function consentLabel(connection: Connection): string {
  if (connection.provider === "TRADING_212") {
    return "Credentials";
  }
  if (connection.status === "REAUTH_REQUIRED") {
    return "Consent expired";
  }
  return "Consent expires";
}

function detailFor(row: SyncRun): string {
  if (row.errorCode) {
    return row.errorCode.replaceAll("_", " ");
  }
  const imported = row.importedCount ?? 0;
  const updated = row.updatedCount ?? 0;
  return `${imported} imported · ${updated} updated`;
}

function outcomeColor(status: string): string {
  if (status === "SUCCEEDED") {
    return "var(--gain)";
  }
  if (status === "RATE_LIMITED") {
    return "var(--faint)";
  }
  if (status === "FAILED") {
    return "var(--loss)";
  }
  return "var(--warn)";
}
