"use client";

import Link from "next/link";
import { useEffect, useState, type CSSProperties, type ReactNode } from "react";
import { apiGet, apiSend, csrfToken, downloadCsv } from "@/lib/api";
import { useAppData } from "@/lib/app-data";
import { customCategories, defaultParentId, parentCategories } from "@/lib/categories";
import { formatMerchantRule, splitMatchPhrases } from "@/lib/merchant-rule";
import { formatInstant } from "@/lib/period";
import { TotpSettings } from "@/components/settings/TotpSettings";
import type { CategorizationRule, Category, Device, TransactionPage } from "@/lib/types";

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
  const [newLabel, setNewLabel] = useState("");
  const [newParentId, setNewParentId] = useState(defaultParentId(categories));
  const [newPhrases, setNewPhrases] = useState("");
  const [phraseCategoryId, setPhraseCategoryId] = useState("");
  const [phraseValue, setPhraseValue] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editLabel, setEditLabel] = useState("");
  const [editParentId, setEditParentId] = useState("");
  const [showNewCategory, setShowNewCategory] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

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

  useEffect(() => {
    setNewParentId((current) => current || defaultParentId(categories));
    setPhraseCategoryId((current) => current || customCategories(categories)[0]?.id || defaultParentId(categories));
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

  async function deleteRule(id: string) {
    await apiSend("DELETE", `/categorization-rules/${id}`);
    setRules((current) => current.filter((item) => item.id !== id));
  }

  async function addDescriptionRules(categoryId: string, phrases: string[]) {
    const createdList: CategorizationRule[] = [];
    for (const phrase of phrases) {
      const created = await apiSend<CategorizationRule>("POST", "/categorization-rules", {
        priority: 0,
        field: "DESCRIPTION",
        operator: "CONTAINS",
        matchValue: phrase,
        targetCategoryId: categoryId,
      });
      if (created) {
        createdList.push(created);
      }
    }
    setRules((current) => {
      const ids = new Set(createdList.map((item) => item.id));
      return [...current.filter((item) => !ids.has(item.id)), ...createdList];
    });
  }

  async function createCategory() {
    const label = newLabel.trim();
    if (!label || !newParentId) {
      return;
    }
    setSaving(true);
    try {
      const created = await apiSend<Category>("POST", "/categories", { label, parentId: newParentId });
      const phrases = splitMatchPhrases(newPhrases);
      if (created && phrases.length > 0) {
        await addDescriptionRules(created.id, phrases);
      }
      setNewLabel("");
      setNewPhrases("");
      setShowNewCategory(false);
      await refresh();
    } finally {
      setSaving(false);
    }
  }

  async function addDescription() {
    const phrases = splitMatchPhrases(phraseValue);
    if (!phraseCategoryId || phrases.length === 0) {
      return;
    }
    setSaving(true);
    try {
      await addDescriptionRules(phraseCategoryId, phrases);
      setPhraseValue("");
    } finally {
      setSaving(false);
    }
  }

  function startEdit(item: Category) {
    setEditingId(item.id);
    setEditLabel(item.label);
    setEditParentId(item.parentId ?? defaultParentId(categories));
  }

  async function saveEdit() {
    if (!editingId || !editLabel.trim()) {
      return;
    }
    setSaving(true);
    try {
      await apiSend("PATCH", `/categories/${editingId}`, { label: editLabel.trim(), parentId: editParentId || null });
      setEditingId(null);
      await refresh();
    } finally {
      setSaving(false);
    }
  }

  async function deleteCategory(item: Category) {
    if (!window.confirm(`Delete “${item.label}”? Transactions in this category become Uncategorized, and its rules are removed.`)) {
      return;
    }
    setSaving(true);
    try {
      await apiSend("DELETE", `/categories/${item.id}`);
      if (editingId === item.id) {
        setEditingId(null);
      }
      if (phraseCategoryId === item.id) {
        setPhraseCategoryId(defaultParentId(categories));
      }
      setRules((current) => current.filter((rule) => rule.targetCategoryId !== item.id));
      await refresh();
    } finally {
      setSaving(false);
    }
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
    <div className="settings-grid">
      <div className="settings-col">
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

      <Group title="Categorization">
        <Link href="/transactions" style={{ textDecoration: "none", color: "inherit" }}>
          <Row label="Uncategorized" sub="Open the transaction list filtered to this bucket" value={String(uncategorized)} warn={uncategorized > 0} />
        </Link>
        <Row label="Internal transfer matching" sub="Amount, currency and 72h · reversible" value="On" />
        <SectionLabel count={customCategories(categories).length}>Categories</SectionLabel>
        {customCategories(categories).length === 0 ? (
          <p className="muted" style={{ margin: 0, padding: "4px 20px 12px" }}>
            Custom categories sit under a system one so totals stay correct.
          </p>
        ) : null}
        {customCategories(categories).map((item) => {
          const parent = categories.find((category) => category.id === item.parentId);
          const editing = editingId === item.id;
          return (
            <div key={item.id} className="settings-row" style={listRow}>
              {editing ? (
                <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", width: "100%" }}>
                  <input value={editLabel} onChange={(event) => setEditLabel(event.target.value)} maxLength={80} style={{ ...inputStyle, flex: "1 1 140px", width: "auto" }} />
                  <select className="mono" value={editParentId} onChange={(event) => setEditParentId(event.target.value)} style={{ ...selectStyle, flex: "1 1 140px" }}>
                    {parentCategories(categories).map((option) => (
                      <option key={option.id} value={option.id}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                  <button type="button" className="btn btn-ghost" style={{ height: 32 }} disabled={saving} onClick={() => setEditingId(null)}>
                    Cancel
                  </button>
                  <button type="button" className="btn btn-primary" style={{ height: 32 }} disabled={saving || !editLabel.trim()} onClick={() => void saveEdit()}>
                    Save
                  </button>
                </div>
              ) : (
                <>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 500, fontSize: 13.5 }}>{item.label}</div>
                    <div style={{ fontSize: 11.5, color: "var(--faint)", marginTop: 1 }}>{parent?.label ?? "Other expense"}</div>
                  </div>
                  <button type="button" className="btn btn-ghost" style={quietBtn} disabled={saving} onClick={() => startEdit(item)}>
                    Edit
                  </button>
                  <button type="button" className="btn btn-danger" style={quietBtn} disabled={saving} onClick={() => void deleteCategory(item)}>
                    Delete
                  </button>
                </>
              )}
            </div>
          );
        })}
        {showNewCategory ? (
          <div style={composer}>
            <div style={{ fontWeight: 500, fontSize: 13, marginBottom: 8 }}>New category</div>
            <div style={{ display: "flex", gap: 8 }}>
              <input
                value={newLabel}
                onChange={(event) => setNewLabel(event.target.value)}
                maxLength={80}
                placeholder="Name, e.g. Pets"
                style={{ ...inputStyle, flex: 1, width: "auto" }}
              />
              <select className="mono" value={newParentId} onChange={(event) => setNewParentId(event.target.value)} style={selectStyle}>
                {parentCategories(categories).map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>
            <input
              value={newPhrases}
              onChange={(event) => setNewPhrases(event.target.value)}
              placeholder="Optional bank phrases, comma or line separated"
              style={{ ...inputStyle, marginTop: 8 }}
            />
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 10 }}>
              <button type="button" className="btn btn-ghost" style={{ height: 32 }} onClick={() => setShowNewCategory(false)}>
                Cancel
              </button>
              <button type="button" className="btn btn-primary" style={{ height: 32 }} disabled={saving || !newLabel.trim() || !newParentId} onClick={() => void createCategory()}>
                Create
              </button>
            </div>
          </div>
        ) : (
          <div style={{ padding: "8px 20px 12px" }}>
            <button type="button" className="btn btn-ghost" style={{ height: 32 }} onClick={() => setShowNewCategory(true)}>
              + New category
            </button>
          </div>
        )}
        <div style={composer}>
          <div style={{ fontWeight: 500, fontSize: 13 }}>Add a description</div>
          <div className="muted" style={{ margin: "2px 0 8px" }}>
            When the bank memo contains this phrase, assign the category.
          </div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <select
              className="mono"
              value={phraseCategoryId}
              onChange={(event) => setPhraseCategoryId(event.target.value)}
              style={{ ...selectStyle, flex: "1 1 140px" }}
            >
              {categories
                .filter((item) => item.code !== "uncategorized")
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
            </select>
            <input
              value={phraseValue}
              onChange={(event) => setPhraseValue(event.target.value)}
              maxLength={200}
              placeholder="Contains, e.g. veterinario"
              style={{ ...inputStyle, flex: 1, width: "auto" }}
            />
            <button type="button" className="btn btn-primary" style={{ height: 34 }} disabled={saving || !phraseCategoryId || splitMatchPhrases(phraseValue).length === 0} onClick={() => void addDescription()}>
              Add
            </button>
          </div>
        </div>
        <SectionLabel count={rules.length}>Rules</SectionLabel>
        {rules.length === 0 ? (
          <p className="muted" style={{ margin: 0, padding: "4px 20px 12px" }}>
            Confirming “always” on a transaction writes a rule here.
          </p>
        ) : null}
        {rules.map((rule) => {
          const label = categories.find((item) => item.id === rule.targetCategoryId)?.label ?? "category";
          return (
            <div key={rule.id} className="settings-row" style={listRow}>
              <div style={{ flex: 1, minWidth: 0, fontSize: 13 }}>{formatMerchantRule(rule, label)}</div>
              <button type="button" className="btn btn-ghost" style={quietBtn} onClick={() => void deleteRule(rule.id)}>
                Delete
              </button>
            </div>
          );
        })}
      </Group>
      </div>

      <div className="settings-col">
      <Group title="Security">
        <TotpSettings />
        <ToggleRow
          label="Privacy mode"
          sub="Hide every monetary value in this browser"
          on={privacy}
          onToggle={() => setPrivacy(!privacy)}
        />
        <Row label="Sessions & devices" sub="Revoke a browser to stop its refresh tokens" value={`${activeDevices.length}`} />
        {activeDevices.length === 0 ? (
          <p className="muted" style={{ margin: 0, padding: "4px 20px 14px" }}>
            No active sessions.
          </p>
        ) : null}
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
        <div style={{ padding: "14px 20px" }}>
          <div style={{ fontWeight: 500, fontSize: 13.5 }}>Delete account</div>
          <p className="muted" style={{ margin: "4px 0 10px", fontSize: 12, lineHeight: 1.45 }}>
            Permanently removes your user, connections, and history on this server. Type DELETE to confirm.
            This cannot be undone.
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

function SectionLabel({ children, count }: { children: ReactNode; count: number }) {
  return (
    <div style={{ padding: "12px 20px 6px", display: "flex", alignItems: "baseline", gap: 8 }}>
      <span style={{ fontWeight: 600, fontSize: 13.5 }}>{children}</span>
      <span
        className="mono"
        style={{
          fontSize: 10.5,
          fontWeight: 500,
          color: "var(--faint)",
          background: "rgba(19,26,25,.06)",
          borderRadius: 5,
          padding: "1px 6px",
        }}
      >
        {count}
      </span>
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

const inputStyle: CSSProperties = {
  ...selectStyle,
  font: "500 13px var(--font-sans)",
  width: "100%",
};

const listRow: CSSProperties = {
  padding: "9px 20px",
  borderBottom: "1px solid rgba(19,26,25,.05)",
  display: "flex",
  alignItems: "center",
  gap: 10,
};

const composer: CSSProperties = {
  margin: "4px 16px 14px",
  padding: "12px 14px",
  background: "#FBF9F5",
  border: "1px solid rgba(19,26,25,.07)",
  borderRadius: 12,
};

const quietBtn: CSSProperties = {
  height: 30,
  padding: "0 10px",
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
