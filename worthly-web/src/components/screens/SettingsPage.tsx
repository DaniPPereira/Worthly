"use client";

import { useEffect, useState, type CSSProperties, type ReactNode } from "react";
import { apiGet, apiSend, csrfToken, downloadCsv } from "@/lib/api";
import { useAppData } from "@/lib/app-data";
import { formatInstant } from "@/lib/period";
import { TotpSettings } from "@/components/settings/TotpSettings";
import type { Device } from "@/lib/types";

const TIMEZONES = ["Europe/Lisbon", "Europe/London", "UTC"];
const CURRENCIES = ["EUR", "GBP", "USD"];

export function SettingsPage() {
  const { owner, privacy, setPrivacy, notifications, refresh } = useAppData();
  const [timezone, setTimezone] = useState(owner.reportingTimezone);
  const [currency, setCurrency] = useState(owner.reportingCurrency);
  const [name, setName] = useState(owner.name ?? "");
  const [devices, setDevices] = useState<Device[]>([]);
  const [saving, setSaving] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    void apiGet<Device[]>("/devices").then(setDevices).catch(() => setDevices([]));
  }, []);

  async function savePrefs() {
    setSaving(true);
    try {
      await apiSend("PATCH", "/me", {
        name: name.trim() || undefined,
        reportingTimezone: timezone,
        reportingCurrency: currency,
      });
      await refresh();
    } finally {
      setSaving(false);
    }
  }

  async function revoke(id: string) {
    await apiSend("DELETE", `/devices/${id}`);
    setDevices((current) => current.filter((item) => item.id !== id));
  }

  async function deleteAccount() {
    if (deleteConfirm !== "DELETE") {
      return;
    }
    setDeleting(true);
    setDeleteError(null);
    try {
      await apiSend("DELETE", "/me", { confirm: true });
      window.location.href = "/logout";
    } catch {
      setDeleteError("Could not delete the account. Try again or sign out and contact the operator.");
      setDeleting(false);
    }
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
  const activeDevices = devices.filter((item) => !item.revoked);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 640 }}>
      <Group title="Account">
        <Row label="Name" sub="Shown in the sidebar">
          <input value={name} onChange={(event) => setName(event.target.value)} maxLength={80} autoComplete="name" style={{ ...inputStyle, width: 180 }} />
        </Row>
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
        <TotpSettings />
        <ToggleRow
          label="Privacy mode"
          sub="Hide every monetary value in this browser"
          on={privacy}
          onToggle={() => setPrivacy(!privacy)}
        />
      </Group>

      {activeDevices.length > 0 ? (
        <Group title="Sessions">
          {activeDevices.map((device) => (
            <div key={device.id} style={{ padding: "12px 20px", borderBottom: "1px solid rgba(19,26,25,.05)", display: "flex", gap: 12, alignItems: "center" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 500, fontSize: 13 }}>{deviceName(device)}</div>
                <div style={{ fontSize: 11.5, color: "var(--faint)" }}>
                  Last seen {formatInstant(device.lastSeenAt, owner.reportingTimezone)}
                </div>
              </div>
              <button type="button" className="btn btn-danger" style={{ height: 32 }} onClick={() => void revoke(device.id)}>
                Revoke
              </button>
            </div>
          ))}
        </Group>
      ) : null}

      {unread.length > 0 ? (
        <Group title="Notifications">
          {unread.map((item) => (
            <div key={item.id} style={{ padding: "12px 20px", borderBottom: "1px solid rgba(19,26,25,.05)", display: "flex", gap: 12, alignItems: "center" }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 500, fontSize: 13.5 }}>{item.type.replaceAll("_", " ")}</div>
                <div style={{ fontSize: 11.5, color: "var(--faint)" }}>{formatInstant(item.createdAt, owner.reportingTimezone)}</div>
              </div>
              <button type="button" className="btn btn-ghost" style={{ height: 32 }} onClick={() => void markRead(item.id)}>
                Mark read
              </button>
            </div>
          ))}
        </Group>
      ) : null}

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
              <span style={{ display: "block", fontSize: 11.5, color: "var(--faint)", marginTop: 2 }}>Clears this browser session</span>
            </span>
          </button>
        </form>
        <div style={{ padding: "14px 20px" }}>
          <div style={{ fontWeight: 500, fontSize: 13.5 }}>Delete account</div>
          <p className="muted" style={{ margin: "4px 0 10px", fontSize: 12, lineHeight: 1.45 }}>
            Permanently removes your user, connections, and history on this server. Type DELETE to confirm.
          </p>
          <input
            value={deleteConfirm}
            onChange={(event) => setDeleteConfirm(event.target.value)}
            placeholder="DELETE"
            autoComplete="off"
            style={{ ...inputStyle, maxWidth: 180 }}
          />
          {deleteError ? <p style={{ color: "var(--loss)", fontSize: 12, margin: "8px 0 0" }}>{deleteError}</p> : null}
          <button
            type="button"
            className="btn btn-danger"
            style={{ height: 32, marginTop: 10 }}
            disabled={deleting || deleteConfirm !== "DELETE"}
            onClick={() => void deleteAccount()}
          >
            Delete my account
          </button>
        </div>
        <p className="muted" style={{ margin: 0, padding: "12px 20px 16px", fontSize: 12 }}>
          <a href="/privacy">Privacy Policy</a>
          {" · "}
          <a href="/terms">Terms of Use</a>
        </p>
      </Group>
    </div>
  );
}

function deviceName(device: Device): string {
  if (device.name && device.name !== "WEB") {
    return device.name;
  }
  if (device.platform === "WEB" || device.name === "WEB") {
    return "Browser";
  }
  return device.platform ?? "Session";
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="card" style={{ overflow: "hidden", minWidth: 0 }}>
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
  children,
}: {
  label: string;
  sub?: string;
  children?: ReactNode;
}) {
  return (
    <div style={{ padding: "14px 20px", borderBottom: "1px solid rgba(19,26,25,.05)", display: "flex", alignItems: "center", gap: 14 }}>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: "block", fontWeight: 500, fontSize: 13.5 }}>{label}</span>
        {sub ? <span style={{ display: "block", fontSize: 11.5, lineHeight: 1.45, color: "var(--faint)", marginTop: 2 }}>{sub}</span> : null}
      </span>
      {children}
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

const inputStyle: CSSProperties = {
  ...selectStyle,
  font: "500 13px var(--font-sans)",
  width: "100%",
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
