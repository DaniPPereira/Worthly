"use client";

import { useEffect, useMemo, useState } from "react";
import { apiGet, apiSend } from "@/lib/api";
import { useAppData } from "@/lib/app-data";
import { formatAmount, formatSignedAmount } from "@/lib/money";
import { formatInstant } from "@/lib/period";
import type { Account, Category, Transaction, TransferMatch } from "@/lib/types";

export function TransactionDrawer({
  transaction,
  accounts,
  onClose,
  onChanged,
}: {
  transaction: Transaction;
  accounts: Account[];
  onClose: () => void;
  onChanged: (next: Transaction) => void;
}) {
  const { owner, privacy, categories } = useAppData();
  const [note, setNote] = useState(transaction.notes ?? "");
  const [saving, setSaving] = useState(false);
  const [suggestions, setSuggestions] = useState<TransferMatch[]>([]);
  const account = accounts.find((item) => item.id === transaction.accountId);
  const category = categories.find((item) => item.id === transaction.categoryId);
  const credit = transaction.direction === "CREDIT";
  const isTransfer = transaction.economicType === "INTERNAL_TRANSFER" || Boolean(transaction.transferMatchId);

  useEffect(() => {
    setNote(transaction.notes ?? "");
  }, [transaction.id, transaction.notes]);

  useEffect(() => {
    void apiGet<TransferMatch[]>("/transfer-matches?status=SUGGESTED")
      .then(setSuggestions)
      .catch(() => setSuggestions([]));
  }, [transaction.id]);

  const related = suggestions.filter(
    (match) => match.leftTransactionId === transaction.id || match.rightTransactionId === transaction.id,
  );

  const chips = useMemo(() => {
    const preferred = ["Groceries", "Housing", "Transport", "Dining", "Investing", "Income", "Uncategorized"];
    const byLabel = new Map(categories.map((item) => [item.label, item]));
    const ordered: Category[] = [];
    for (const label of preferred) {
      const found = byLabel.get(label) ?? categories.find((item) => item.label.toLowerCase().includes(label.toLowerCase()));
      if (found && !ordered.some((item) => item.id === found.id)) {
        ordered.push(found);
      }
    }
    for (const item of categories) {
      if (ordered.length >= 8) {
        break;
      }
      if (!ordered.some((existing) => existing.id === item.id)) {
        ordered.push(item);
      }
    }
    return ordered;
  }, [categories]);

  async function patch(body: { categoryId?: string | null; notes?: string | null }) {
    setSaving(true);
    try {
      const next = await apiSend<Transaction>("PATCH", `/transactions/${transaction.id}`, body);
      if (next) {
        onChanged(next);
      }
    } finally {
      setSaving(false);
    }
  }

  async function unlink() {
    if (!transaction.transferMatchId) {
      return;
    }
    setSaving(true);
    try {
      await apiSend("DELETE", `/transfer-matches/${transaction.transferMatchId}`);
      onChanged({ ...transaction, transferMatchId: null, economicType: "EXPENSE" });
    } finally {
      setSaving(false);
    }
  }

  async function confirmMatch(match: TransferMatch) {
    setSaving(true);
    try {
      await apiSend("POST", "/transfer-matches", {
        leftTransactionId: match.leftTransactionId,
        rightTransactionId: match.rightTransactionId,
      });
      onChanged({ ...transaction, transferMatchId: match.id, economicType: "INTERNAL_TRANSFER" });
    } finally {
      setSaving(false);
    }
  }

  async function rejectMatch(match: TransferMatch) {
    setSaving(true);
    try {
      await apiSend("DELETE", `/transfer-matches/${match.id}`);
      setSuggestions((current) => current.filter((item) => item.id !== match.id));
    } finally {
      setSaving(false);
    }
  }

  const amountText = credit
    ? formatSignedAmount(transaction.money.amount, transaction.money.currency, privacy)
    : formatAmount(
        transaction.money.amount.startsWith("-") ? transaction.money.amount : `-${transaction.money.amount}`,
        transaction.money.currency,
        privacy,
      );

  return (
    <div style={{ position: "absolute", inset: 0, zIndex: 70, display: "flex", justifyContent: "flex-end" }}>
      <button type="button" aria-label="Close transaction" onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(19,26,25,.34)", border: "none" }} />
      <div
        style={{
          position: "relative",
          width: 420,
          height: "100%",
          background: "var(--paper)",
          borderLeft: "1px solid rgba(19,26,25,.12)",
          padding: "24px 26px",
          overflow: "auto",
          animation: "wslide .2s ease-out",
        }}
      >
        <div style={{ display: "flex", alignItems: "flex-start", gap: 12 }}>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 12, color: "var(--faint)" }}>
              {category?.label ?? "Uncategorized"} · {account?.displayName ?? "Account"}
            </div>
            <div className="serif tabular" style={{ fontSize: 40, lineHeight: 1.05, color: credit ? "var(--gain)" : "var(--ink)", marginTop: 6 }}>
              {amountText}
            </div>
            <div style={{ fontWeight: 600, fontSize: 16, marginTop: 6 }}>{transaction.merchant || transaction.description || "Transaction"}</div>
          </div>
          <button type="button" className="btn btn-ghost" onClick={onClose} style={{ width: 30, height: 30, padding: 0 }} aria-label="Close">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#3E4A47" strokeWidth="2" strokeLinecap="round">
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </div>
        <div className="card" style={{ marginTop: 18, padding: "4px 16px", borderRadius: 14 }}>
          {[
            { k: "Booked date", v: formatInstant(transaction.reportingAt, owner.reportingTimezone) },
            { k: "Place", v: transaction.location },
            { k: "Bank details", v: transaction.description && transaction.description !== transaction.merchant ? transaction.description : null },
            { k: "Account", v: account ? `${account.displayName}${account.maskedIdentifier ? ` · ${account.maskedIdentifier}` : ""}` : transaction.accountId },
            { k: "Status", v: transaction.lifecycleStatus },
            { k: "Type", v: transaction.economicType.replaceAll("_", " ") },
            { k: "Currency", v: transaction.money.currency },
          ].filter((row) => row.v).map((row) => (
            <div key={row.k} style={{ borderTop: "1px solid rgba(19,26,25,.06)", padding: "11px 0", display: "flex", justifyContent: "space-between", gap: 14 }}>
              <span style={{ fontSize: 12.5, color: "#5E6A67" }}>{row.k}</span>
              <span className="mono" style={{ fontSize: 12.5, fontWeight: 500, textAlign: "right" }}>
                {row.v}
              </span>
            </div>
          ))}
        </div>
        <div className="label" style={{ margin: "20px 0 9px" }}>
          Category — your choice wins
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 7 }}>
          {chips.map((chip) => {
            const active = transaction.categoryId === chip.id;
            return (
              <button
                key={chip.id}
                type="button"
                disabled={saving}
                onClick={() => void patch({ categoryId: chip.id })}
                style={{
                  border: `1px solid ${active ? "var(--pine)" : "rgba(19,26,25,.12)"}`,
                  background: active ? "var(--pine)" : "var(--white)",
                  color: active ? "var(--cream)" : "var(--ink)",
                  font: "500 12px var(--font-sans)",
                  padding: "8px 12px",
                  borderRadius: 9,
                }}
              >
                {chip.label}
              </button>
            );
          })}
        </div>
        <div className="card" style={{ marginTop: 16, padding: "13px 15px", borderRadius: 13 }}>
          <div style={{ fontSize: 11, fontWeight: 500, color: "var(--faint)" }}>Note</div>
          <textarea
            value={note}
            onChange={(event) => setNote(event.target.value)}
            onBlur={() => {
              if (note !== (transaction.notes ?? "")) {
                void patch({ notes: note || null });
              }
            }}
            maxLength={1000}
            rows={3}
            style={{ width: "100%", border: "none", resize: "vertical", marginTop: 6, background: "transparent", color: "var(--ink)" }}
            placeholder="Add a note"
          />
        </div>
        <button
          type="button"
          disabled={saving || (!isTransfer && related.length === 0)}
          onClick={() => {
            if (transaction.transferMatchId) {
              void unlink();
            }
          }}
          style={{
            width: "100%",
            marginTop: 10,
            background: "var(--white)",
            border: "1px solid rgba(19,26,25,.08)",
            borderRadius: 13,
            padding: "13px 15px",
            display: "flex",
            alignItems: "center",
            gap: 12,
          }}
        >
          <span style={{ flex: 1, textAlign: "left" }}>
            <span style={{ display: "block", fontWeight: 500, fontSize: 13.5 }}>Internal transfer</span>
            <span style={{ display: "block", fontSize: 11.5, color: "var(--faint)", marginTop: 1 }}>
              {isTransfer ? "Excluded from spending totals" : related.length > 0 ? "A suggested match is waiting" : "Link a counterpart from transfer suggestions"}
            </span>
          </span>
          <span
            style={{
              width: 42,
              height: 25,
              borderRadius: 13,
              background: isTransfer ? "var(--pine)" : "rgba(19,26,25,.18)",
              flex: "none",
              padding: 3,
              display: "flex",
              justifyContent: isTransfer ? "flex-end" : "flex-start",
            }}
          >
            <span style={{ width: 19, height: 19, borderRadius: "50%", background: "#fff", boxShadow: "0 1px 2px rgba(0,0,0,.2)" }} />
          </span>
        </button>
        {related.map((match) => (
          <div key={match.id} className="card" style={{ marginTop: 10, padding: 12, display: "flex", gap: 8 }}>
            <div style={{ flex: 1, fontSize: 12.5 }}>Suggested match · {match.confidence}%</div>
            <button type="button" className="btn btn-primary" style={{ height: 32 }} onClick={() => void confirmMatch(match)}>
              Confirm
            </button>
            <button type="button" className="btn btn-ghost" style={{ height: 32 }} onClick={() => void rejectMatch(match)}>
              Reject
            </button>
          </div>
        ))}
        <div style={{ background: "#FBF9F5", border: "1px solid rgba(19,26,25,.07)", borderRadius: 13, marginTop: 16, padding: "13px 15px" }}>
          <div className="label">Provider record</div>
          <div className="muted" style={{ marginTop: 6 }}>
            Your edits are stored alongside the raw provider record, which is never altered and survives the next sync.
          </div>
        </div>
      </div>
    </div>
  );
}
