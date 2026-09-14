"use client";

import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/ui/Primitives";
import { ApiError, apiGet, apiSend } from "@/lib/api";
import { connectionLabel, statusTone, useAppData } from "@/lib/app-data";
import { formatInstant } from "@/lib/period";
import type { BankChoice, Connection, SyncRun, SyncRunPage } from "@/lib/types";

type LogRow = SyncRun & { provider: string };

export function ConnectionsPage() {
  const { connections, refresh, syncing, owner } = useAppData();
  const searchParams = useSearchParams();
  const [banks, setBanks] = useState<BankChoice[]>([]);
  const [banksError, setBanksError] = useState<string | null>(null);
  const [log, setLog] = useState<LogRow[]>([]);
  const [handoff, setHandoff] = useState<Connection | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirmPurge, setConfirmPurge] = useState<Connection | null>(null);
  const [t212Error, setT212Error] = useState<string | null>(null);
  const [authError, setAuthError] = useState<string | null>(null);
  const result = searchParams.get("status");

  useEffect(() => {
    void apiGet<BankChoice[]>("/connections/banks?country=PT")
      .then((list) => {
        setBanks(list);
        setBanksError(null);
      })
      .catch((err: unknown) => {
        setBanks([]);
        setBanksError(err instanceof ApiError ? err.message : "provider_error");
      });
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
    setAuthError(null);
    try {
      const start = await apiSend<{ url: string }>("POST", "/connections/enable-banking/authorize", {
        name,
        country,
        returnClient: "WEB",
      });
      if (start?.url) {
        window.location.assign(start.url);
      }
    } catch (err: unknown) {
      const code = err instanceof ApiError ? err.message : "provider_error";
      if (code === "redirect_url_mismatch") {
        setAuthError(
          "The Enable Banking app redirect URL does not match Worthly. In the Enable Banking control panel it must be exactly http://localhost:8080/api/v1/connections/enable-banking/callback",
        );
      } else {
        setAuthError(
          "Enable Banking rejected the bank login start. Confirm the sandbox app redirect URL is http://localhost:8080/api/v1/connections/enable-banking/callback, then try Connect again.",
        );
      }
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

  async function connectTrading212(apiKey: string, apiSecret: string, environment: string) {
    setBusyId("trading-212");
    setT212Error(null);
    try {
      const connection = await apiSend<Connection>("POST", "/connections/trading-212", { apiKey, apiSecret, environment });
      await refresh();
      if (connection?.status === "ERROR") {
        setT212Error(
          "Trading 212 rejected those credentials. Use a read-only Live key from Trading 212 Invest (or Stocks ISA) and try again.",
        );
      }
    } catch {
      setT212Error("Could not connect Trading 212.");
    } finally {
      setBusyId(null);
    }
  }

  const unusedBanks = useMemo(() => {
    const names = new Set(connections.map((item) => item.institutionName).filter(Boolean));
    return banks.filter((bank) => !names.has(bank.name));
  }, [banks, connections]);
  const hasTrading212 = connections.some((item) => item.provider === "TRADING_212");

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
        <EmptyState title="No providers yet">Connect Santander Portugal or Revolut through Enable Banking, or add Trading 212 with a read-only API key from this screen.</EmptyState>
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
              onSaveTrading212={
                connection.provider === "TRADING_212" && (connection.status === "CONFIGURATION_REQUIRED" || connection.status === "ERROR")
                  ? connectTrading212
                  : undefined
              }
              t212Busy={busyId === "trading-212"}
              t212Error={connection.provider === "TRADING_212" ? t212Error : null}
            />
          ))}
        </div>
      )}
      <div className="card" style={{ padding: 20 }}>
        <div className="label">Connect a bank</div>
        {unusedBanks.length > 0 ? (
          <>
            <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
              {unusedBanks.map((bank) => (
                <button key={`${bank.country}-${bank.name}`} type="button" className="btn btn-primary" onClick={() => void authorize(bank.name, bank.country)}>
                  Connect {bank.name}
                </button>
              ))}
            </div>
            {authError ? <p style={{ color: "var(--loss)", fontSize: 13, marginTop: 10, maxWidth: 640 }}>{authError}</p> : null}
            <p className="muted" style={{ marginTop: 12 }}>Opens your bank in this browser. You confirm there — Worthly never sees your credentials.</p>
          </>
        ) : banksError === "configuration_required" ? (
          <p className="muted" style={{ marginTop: 12, maxWidth: 640 }}>
            Santander and Revolut are added here through Enable Banking (Open Banking). This server has no Enable Banking
            application yet, so the bank buttons cannot appear. Create an app at enablebanking.com, put its application ID
            and RSA private key on the API (`WORTHLY_ENABLE_BANKING_APPLICATION_ID` and `WORTHLY_ENABLE_BANKING_PRIVATE_KEY_FILE`),
            restart Worthly, then refresh this page. You still log in at the bank — Worthly never sees that password.
          </p>
        ) : banksError ? (
          <p className="muted" style={{ marginTop: 12 }}>
            Banks could not be loaded ({banksError.replaceAll("_", " ")}). Try again after the API can reach Enable Banking.
          </p>
        ) : banks.length === 0 ? (
          <p className="muted" style={{ marginTop: 12, maxWidth: 640 }}>
            Enable Banking is configured, but this sandbox app did not return Santander or Revolut for Portugal.
            Open the Mock ASPSP tab in the Enable Banking control panel, add a test bank, then refresh. Real Santander
            and Revolut need a Production Enable Banking application.
          </p>
        ) : (
          <p className="muted" style={{ marginTop: 12 }}>All supported banks for Portugal are already connected.</p>
        )}
      </div>
      {!hasTrading212 ? (
        <div className="card" style={{ padding: 20 }}>
          <div className="label">Connect Trading 212</div>
          <p className="muted" style={{ marginTop: 8 }}>Paste a read-only Live API key from Trading 212 Invest (or Stocks ISA). Crypto is a separate Trading 212 account and is not included. Worthly encrypts the key on the server and keeps it until you replace it.</p>
          {t212Error ? <p style={{ color: "var(--loss)", fontSize: 13, marginTop: 10 }}>{t212Error}</p> : null}
          <Trading212Form busy={busyId === "trading-212"} onSubmit={connectTrading212} />
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
  onSaveTrading212,
  t212Busy,
  t212Error,
}: {
  connection: Connection;
  timezone: string;
  busy: boolean;
  onSync: () => void;
  onDisconnect: () => void;
  onPurge: () => void;
  onReauth: () => void;
  onSaveTrading212?: (apiKey: string, apiSecret: string, environment: string) => Promise<void>;
  t212Busy?: boolean;
  t212Error?: string | null;
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
          v={connection.consentExpiresAt ? formatInstant(connection.consentExpiresAt, timezone) : bank ? "—" : "Encrypted on server"}
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
        ) : connection.status === "CONFIGURATION_REQUIRED" || (connection.provider === "TRADING_212" && connection.status === "ERROR" && onSaveTrading212) ? (
          <>
            {t212Error ? <p style={{ color: "var(--loss)", fontSize: 12, marginBottom: 8 }}>{t212Error}</p> : null}
            <p className="muted">Add a read-only Trading 212 API key. Worthly encrypts it on the server.</p>
            {onSaveTrading212 ? <Trading212Form busy={Boolean(t212Busy)} onSubmit={onSaveTrading212} /> : null}
          </>
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

function Trading212Form({
  busy,
  onSubmit,
}: {
  busy: boolean;
  onSubmit: (apiKey: string, apiSecret: string, environment: string) => Promise<void>;
}) {
  const [apiKey, setApiKey] = useState("");
  const [apiSecret, setApiSecret] = useState("");

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        void onSubmit(apiKey, apiSecret, "LIVE").then(() => {
          setApiKey("");
          setApiSecret("");
        });
      }}
      style={{ display: "grid", gap: 10, marginTop: 14 }}
    >
      <input
        type="text"
        autoComplete="off"
        required
        placeholder="API key"
        value={apiKey}
        onChange={(event) => setApiKey(event.target.value)}
        style={{ height: 42, padding: "0 12px", borderRadius: 9, border: "1px solid rgba(19,26,25,.12)" }}
      />
      <input
        type="password"
        autoComplete="off"
        required
        placeholder="API secret"
        value={apiSecret}
        onChange={(event) => setApiSecret(event.target.value)}
        style={{ height: 42, padding: "0 12px", borderRadius: 9, border: "1px solid rgba(19,26,25,.12)" }}
      />
      <button type="submit" className="btn btn-primary" style={{ height: 42 }} disabled={busy}>
        Save read-only key
      </button>
    </form>
  );
}
