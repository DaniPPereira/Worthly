"use client";

import Link from "next/link";
import { useEffect, useState, type CSSProperties, type ReactNode } from "react";
import { apiGet, apiSend, csrfToken, downloadCsv } from "@/lib/api";
import { useAppData } from "@/lib/app-data";
import { formatInstant } from "@/lib/period";
import type { CategorizationRule, Device, TransactionPage } from "@/lib/types";

const TIMEZONES = ["Europe/Lisbon", "Europe/London", "UTC"];
const CURRENCIES = ["EUR", "GBP", "USD"];

export function SettingsPage() {
  const { owner, privacy, setPrivacy, categories, notifications, refresh } = useAppData();
  const [timezone, setTimezone] = useState(owner.reportingTimezone);
  const [currency, setCurrency] = useState(owner.reportingCurrency);
  const [devices, setDevices] = useState<Device[]>([]);
  const [rules, setRules] = useState<CategorizationRule[]>([]);
  const [uncategorized, setUncategorized] = useState(0);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const uncategorizedId = categories.find((item) => item.code === "uncategorized")?.id;
    void apiGet<Device[]>("/devices").then(setDevices).catch(() => setDevices([]));
    void apiGet<CategorizationRule[]>("/categorization-rules").then(setRules).catch(() => setRules([]));
    if (uncategorizedId) {
      void apiGet<TransactionPage>(`/transactions?categoryId=${uncategorizedId}&size=1`)
        .then((page) => setUncategorized(page.total))
        .catch(() => setUncategorized(0));
    }
  }, [categories]);

  async function savePrefs() {
    setSaving(true);
    try {
      await apiSend("PATCH", "/me", { reportingTimezone: timezone, reportingCurrency: currency });
      await refresh();
    } finally {
      setSaving(false);
    }
  }

  async function revoke(id: string) {
    await apiSend("DELETE", `/devices/${id}`);
    setDevices((current) => current.filter((item) => item.id !== id));
  }

  async function markRead(id: string) {
    try {
      await apiSend("POST", `/notifications/${id}/read`);
      await refresh();
    } catch {
      // Keep the inbox usable if the mark-read call fails.
    }
  }

  const unread = notifications.filter((item) => item.readAt == null);

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, alignItems: "start" }}>
      <Group title="Reporting">
        <Row label="Timezone" sub="Used for the reporting month and timestamps">
          <select className="mono" value={timezone} onChange={(event) => setTimezone(event.target.value)} style={selectStyle}>
            {TIMEZONES.includes(timezone) ? null : <option value={timezone}>{timezone}</option>}
            {TIMEZONES.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </Row>
        <Row label="Reporting currency" sub="A preference only — it does not convert other currencies">
          <select className="mono" value={currency} onChange={(event) => setCurrency(event.target.value)} style={selectStyle}>
            {CURRENCIES.includes(currency) ? null : <option value={currency}>{currency}</option>}
            {CURRENCIES.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </Row>
        <div style={{ padding: 16 }}>
          <button type="button" className="btn btn-primary" disabled={saving} onClick={() => void savePrefs()}>
            Save preferences
          </button>
        </div>
      </Group>

      <Group title="Security">
        <ToggleRow
          label="Privacy mode"
          sub="Hide every monetary value in this browser"
          on={privacy}
          onToggle={() => setPrivacy(!privacy)}
        />
        <Row label="Sessions & devices" sub="Revoke a device to stop its refresh tokens" value={`${devices.filter((item) => !item.revoked).length}`} />
        {devices.map((device) => (
          <div key={device.id} style={{ padding: "12px 20px", borderBottom: "1px solid rgba(19,26,25,.05)", display: "flex", gap: 12, alignItems: "center" }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 500, fontSize: 13 }}>{device.name ?? device.platform ?? "Session"}</div>
              <div style={{ fontSize: 11.5, color: "var(--faint)" }}>
                Last seen {formatInstant(device.lastSeenAt, owner.reportingTimezone)}
                {device.revoked ? " · revoked" : ""}
              </div>
            </div>
            {device.revoked ? null : (
              <button type="button" className="btn btn-danger" style={{ height: 32 }} onClick={() => void revoke(device.id)}>
                Revoke
              </button>
            )}
          </div>
        ))}
      </Group>

      <Group title="Categorization">
        <Row label="Rules" sub="Deterministic rules · manual decisions always win" value={String(rules.length)} />
        <Row label="Internal transfer matching" sub="Amount, currency and 72h proximity · always reversible" value="On" />
        <Link href="/transactions" style={{ textDecoration: "none", color: "inherit" }}>
          <Row label="Uncategorized transactions" value={String(uncategorized)} warn={uncategorized > 0} />
        </Link>
      </Group>

      <Group title="Notifications">
        {unread.length === 0 ? (
          <Row label="Inbox" sub="Reauthorization and repeated sync failures appear here" value="0" />
        ) : (
          unread.map((item) => (
            <div key={item.id} style={{ padding: "12px 20px", borderBottom: "1px solid rgba(19,26,25,.05)", display: "flex", gap: 12, alignItems: "center" }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 500, fontSize: 13.5 }}>{item.type.replaceAll("_", " ")}</div>
                <div style={{ fontSize: 11.5, color: "var(--faint)" }}>{formatInstant(item.createdAt, owner.reportingTimezone)}</div>
              </div>
              <button type="button" className="btn btn-ghost" style={{ height: 32 }} onClick={() => void markRead(item.id)}>
                Mark read
              </button>
            </div>
          ))
        )}
      </Group>

      <Group title="Data">
        <button type="button" onClick={() => void downloadCsv("/exports/transactions.csv", "transactions.csv").catch(() => undefined)} style={rowButton}>
          <span>
            <span style={{ display: "block", fontWeight: 500, fontSize: 13.5 }}>Export transactions</span>
            <span style={{ display: "block", fontSize: 11.5, color: "var(--faint)", marginTop: 2 }}>Normalized CSV</span>
          </span>
        </button>
        <form method="post" action="/logout">
          <input type="hidden" name="csrf" value={csrfToken()} />
          <button type="submit" style={{ ...rowButton, color: "var(--loss)" }}>
            <span>
              <span style={{ display: "block", fontWeight: 500, fontSize: 13.5 }}>Sign out</span>
              <span style={{ display: "block", fontSize: 11.5, color: "var(--faint)", marginTop: 2 }}>Clears this browser session and revokes its refresh family</span>
            </span>
          </button>
        </form>
      </Group>
    </div>
  );
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="card" style={{ overflow: "hidden" }}>
      <div className="label" style={{ padding: "15px 20px 13px", borderBottom: "1px solid rgba(19,26,25,.07)" }}>
        {title}
      </div>
      {children}
    </div>
  );
}

function Row({
  label,
  sub,
  value,
  warn,
  children,
}: {
  label: string;
  sub?: string;
  value?: string;
  warn?: boolean;
  children?: ReactNode;
}) {
  return (
    <div style={{ padding: "14px 20px", borderBottom: "1px solid rgba(19,26,25,.05)", display: "flex", alignItems: "center", gap: 14 }}>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: "block", fontWeight: 500, fontSize: 13.5, color: warn ? "var(--warn)" : "var(--ink)" }}>{label}</span>
        {sub ? <span style={{ display: "block", fontSize: 11.5, lineHeight: 1.45, color: "var(--faint)", marginTop: 2 }}>{sub}</span> : null}
      </span>
      {children}
      {value ? <span className="mono" style={{ fontSize: 12.5, fontWeight: 500, color: warn ? "var(--warn)" : "var(--faint)" }}>{value}</span> : null}
    </div>
  );
}

function ToggleRow({ label, sub, on, onToggle }: { label: string; sub: string; on: boolean; onToggle: () => void }) {
  return (
    <button type="button" onClick={onToggle} style={rowButton}>
      <span style={{ flex: 1, textAlign: "left" }}>
        <span style={{ display: "block", fontWeight: 500, fontSize: 13.5 }}>{label}</span>
        <span style={{ display: "block", fontSize: 11.5, color: "var(--faint)", marginTop: 2 }}>{sub}</span>
      </span>
      <span
        style={{
          width: 42,
          height: 25,
          borderRadius: 13,
          background: on ? "var(--pine)" : "rgba(19,26,25,.18)",
          flex: "none",
          padding: 3,
          display: "flex",
          justifyContent: on ? "flex-end" : "flex-start",
        }}
      >
        <span style={{ width: 19, height: 19, borderRadius: "50%", background: "#fff", boxShadow: "0 1px 2px rgba(0,0,0,.2)" }} />
      </span>
    </button>
  );
}

const selectStyle: CSSProperties = {
  border: "1px solid rgba(19,26,25,.12)",
  borderRadius: 8,
  padding: "6px 8px",
  background: "#fff",
  fontSize: 12.5,
};

const rowButton: CSSProperties = {
  width: "100%",
  textAlign: "left",
  background: "none",
  border: "none",
  borderBottom: "1px solid rgba(19,26,25,.05)",
  padding: "14px 20px",
  display: "flex",
  alignItems: "center",
  gap: 14,
  cursor: "pointer",
};
