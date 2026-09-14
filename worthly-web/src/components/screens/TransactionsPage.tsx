"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { TransactionDrawer } from "@/components/screens/TransactionDrawer";
import { EmptyState } from "@/components/ui/Primitives";
import { apiGet, downloadCsv } from "@/lib/api";
import { useAppData } from "@/lib/app-data";
import { formatAmount, formatSignedAmount } from "@/lib/money";
import { formatDay, monthDateRange, monthKeyInZone, shiftMonthKey } from "@/lib/period";
import type { Account, TransactionPage } from "@/lib/types";

const FILTERS = ["All", "Expenses", "Income", "Transfers", "Uncategorized"] as const;
type Filter = (typeof FILTERS)[number];

function queryFor(filter: Filter, uncategorizedId: string | undefined): string {
  switch (filter) {
    case "Expenses":
      return "&economicType=EXPENSE";
    case "Income":
      return "&economicType=INCOME";
    case "Transfers":
      return "&economicType=INTERNAL_TRANSFER";
    case "Uncategorized":
      return uncategorizedId ? `&categoryId=${uncategorizedId}` : "";
    default:
      return "";
  }
}

export function TransactionsPage() {
  const { owner, privacy, categories, connections } = useAppData();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [filter, setFilter] = useState<Filter>("All");
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");
  const [page, setPage] = useState<TransactionPage | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [openId, setOpenId] = useState<string | null>(searchParams.get("open"));
  const [exportError, setExportError] = useState<string | null>(null);

  const currentMonth = monthKeyInZone(owner.reportingTimezone);
  const initialRange = monthDateRange(currentMonth);
  const [from, setFrom] = useState(initialRange.from);
  const [to, setTo] = useState(initialRange.to);
  const uncategorizedId = categories.find((item) => item.code === "uncategorized")?.id;

  useEffect(() => {
    const handle = window.setTimeout(() => setDebounced(search.trim()), 250);
    return () => window.clearTimeout(handle);
  }, [search]);

  useEffect(() => {
    void apiGet<Account[]>("/accounts").then(setAccounts).catch(() => setAccounts([]));
  }, []);

  useEffect(() => {
    const q = debounced ? `&q=${encodeURIComponent(debounced)}` : "";
    void apiGet<TransactionPage>(`/transactions?from=${from}&to=${to}&size=50${queryFor(filter, uncategorizedId)}${q}`)
      .then(setPage)
      .catch(() => setPage({ items: [], page: 0, size: 50, total: 0 }));
  }, [debounced, filter, from, to, uncategorizedId]);

  const openTx = useMemo(() => page?.items.find((item) => item.id === openId) ?? null, [openId, page]);
  const accountName = (id: string) => accounts.find((item) => item.id === id)?.displayName ?? "Account";
  const accountMask = (id: string) => accounts.find((item) => item.id === id)?.maskedIdentifier ?? "";

  function applyMonth(monthKey: string) {
    const range = monthDateRange(monthKey);
    setFrom(range.from);
    setTo(range.to);
  }

  function onFromChange(value: string) {
    setFrom(value);
    if (value && to && value > to) {
      setTo(value);
    }
  }

  function onToChange(value: string) {
    setTo(value);
    if (value && from && value < from) {
      setFrom(value);
    }
  }

  async function exportCsv() {
    setExportError(null);
    try {
      await downloadCsv(`/exports/transactions.csv?from=${from}&to=${to}`, "transactions.csv");
    } catch {
      setExportError("Could not download the CSV. Try again.");
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14, position: "relative" }}>
      <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
        <label style={{ flex: 1, background: "#fff", border: "1px solid rgba(19,26,25,.11)", borderRadius: 10, padding: "10px 13px", display: "flex", alignItems: "center", gap: 10 }}>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#6B7572" strokeWidth="2" strokeLinecap="round">
            <circle cx="11" cy="11" r="7" />
            <path d="M16.5 16.5 21 21" />
          </svg>
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search merchant, note, reference or amount"
            style={{ border: "none", outline: "none", flex: 1, background: "transparent", fontSize: 13 }}
          />
        </label>
        <div style={{ display: "flex", alignItems: "center", gap: 6, background: "#fff", border: "1px solid rgba(19,26,25,.11)", borderRadius: 10, padding: "4px 8px" }}>
          <button
            type="button"
            className="btn btn-ghost"
            style={{ height: 32, width: 32, padding: 0, border: "none" }}
            aria-label="Previous month"
            onClick={() => applyMonth(shiftMonthKey(from.slice(0, 7), -1))}
          >
            ‹
          </button>
          <input type="date" value={from} onChange={(event) => onFromChange(event.target.value)} style={dateInput} />
          <span className="muted" style={{ fontSize: 12 }}>to</span>
          <input type="date" value={to} onChange={(event) => onToChange(event.target.value)} style={dateInput} />
          <button
            type="button"
            className="btn btn-ghost"
            style={{ height: 32, width: 32, padding: 0, border: "none" }}
            aria-label="Next month"
            onClick={() => applyMonth(shiftMonthKey(from.slice(0, 7), 1))}
          >
            ›
          </button>
          <button type="button" className="btn btn-ghost" style={{ height: 32 }} onClick={() => applyMonth(currentMonth)}>
            This month
          </button>
        </div>
        <button type="button" className="btn btn-ghost" style={{ height: 40 }} onClick={() => void exportCsv()}>
          Export CSV
        </button>
      </div>
      {exportError ? <p style={{ color: "var(--loss)", fontSize: 13, margin: 0 }}>{exportError}</p> : null}
      <div style={{ display: "flex", gap: 8 }}>
        {FILTERS.map((item) => {
          const active = filter === item;
          return (
            <button
              key={item}
              type="button"
              onClick={() => setFilter(item)}
              style={{
                border: `1px solid ${active ? "var(--pine)" : "rgba(19,26,25,.12)"}`,
                background: active ? "var(--pine)" : "#fff",
                color: active ? "var(--cream)" : "var(--ink)",
                font: "500 12.5px var(--font-sans)",
                padding: "8px 13px",
                borderRadius: 9,
              }}
            >
              {item}
            </button>
          );
        })}
        <div style={{ flex: 1 }} />
        <div style={{ alignSelf: "center", fontSize: 11.5, color: "var(--faint)" }}>
          {page ? `${page.items.length} of ${page.total} transactions` : ""}
        </div>
      </div>
      {!page ? (
        <p className="muted">Loading transactions…</p>
      ) : page.items.length === 0 ? (
        <EmptyState title="No transactions in this view">
          {connections.some((connection) => connection.provider === "ENABLE_BANKING")
            ? "Try another filter, date range, or wait for the next successful sync."
            : "Connect Santander or Revolut from Connections to import card and account purchases. Trading 212 activity stays under Investments."}
        </EmptyState>
      ) : (
        <div className="card" style={{ overflow: "hidden", borderRadius: 14 }}>
          <div
            className="label"
            style={{
              display: "grid",
              gridTemplateColumns: "104px 1.4fr 150px 190px 100px 130px",
              gap: 14,
              padding: "11px 20px",
              background: "#FBF9F5",
              borderBottom: "1px solid rgba(19,26,25,.08)",
            }}
          >
            <span>Date</span>
            <span>Merchant</span>
            <span>Category</span>
            <span>Account</span>
            <span>Status</span>
            <span style={{ textAlign: "right" }}>Amount</span>
          </div>
          {page.items.map((tx, index) => {
            const category = categories.find((item) => item.id === tx.categoryId);
            const credit = tx.direction === "CREDIT";
            const transfer = tx.economicType === "INTERNAL_TRANSFER" || Boolean(tx.transferMatchId);
            const pending = tx.lifecycleStatus === "PENDING";
            const uncategorized = !category || category.code === "uncategorized";
            const initial = transfer ? "⇄" : (tx.merchant || "?").slice(0, 1).toUpperCase();
            return (
              <button
                key={tx.id}
                type="button"
                onClick={() => {
                  setOpenId(tx.id);
                  router.replace(`/transactions?open=${tx.id}`);
                }}
                style={{
                  width: "100%",
                  textAlign: "left",
                  background: index % 2 ? "#FDFCFA" : "#fff",
                  border: "none",
                  borderBottom: "1px solid rgba(19,26,25,.05)",
                  padding: "13px 20px",
                  display: "grid",
                  gridTemplateColumns: "104px 1.4fr 150px 190px 100px 130px",
                  gap: 14,
                  alignItems: "center",
                }}
              >
                <span className="mono" style={{ fontSize: 12, color: "var(--faint)" }}>
                  {formatDay(tx.reportingAt, owner.reportingTimezone)}
                </span>
                  <span style={{ minWidth: 0, display: "flex", alignItems: "center", gap: 9 }}>
                    <span
                      style={{
                        width: 26,
                        height: 26,
                        flex: "none",
                        borderRadius: 7,
                        background: credit ? "rgba(20,101,74,.1)" : transfer ? "rgba(14,74,62,.08)" : "rgba(19,26,25,.06)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        fontWeight: 600,
                        fontSize: 11,
                        color: credit ? "var(--gain)" : transfer ? "var(--pine)" : "#5E6A67",
                      }}
                    >
                      {initial}
                    </span>
                    <span style={{ minWidth: 0, display: "flex", flexDirection: "column" }}>
                      <span style={{ fontWeight: 600, fontSize: 13, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {tx.merchant || tx.description || "Transaction"}
                      </span>
                      {tx.location || (tx.description && tx.description !== tx.merchant) ? (
                        <span style={{ fontSize: 11.5, color: "var(--faint)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                          {[tx.location, tx.description && tx.description !== tx.merchant ? tx.description : null].filter(Boolean).join(" · ")}
                        </span>
                      ) : null}
                    </span>
                  </span>
                <span style={{ fontSize: 12.5, color: uncategorized ? "var(--warn)" : "var(--faint)" }}>{category?.label ?? "Uncategorized"}</span>
                <span className="mono" style={{ fontSize: 11.5, color: "var(--faint)" }}>
                  {accountName(tx.accountId)} {accountMask(tx.accountId)}
                </span>
                <span>
                  {pending ? (
                    <span className="mono" style={{ fontSize: 9.5, letterSpacing: ".08em", textTransform: "uppercase", color: "var(--warn)", border: "1px solid rgba(138,100,18,.35)", borderRadius: 4, padding: "2px 5px" }}>
                      Pending
                    </span>
                  ) : transfer ? (
                    <span className="mono" style={{ fontSize: 9.5, letterSpacing: ".08em", textTransform: "uppercase", color: "var(--pine)", border: "1px solid rgba(14,74,62,.3)", borderRadius: 4, padding: "2px 5px" }}>
                      Transfer
                    </span>
                  ) : (
                    <span style={{ fontSize: 11.5, color: "var(--faint)" }}>Booked</span>
                  )}
                </span>
                <span className="mono tabular" style={{ textAlign: "right", fontSize: 13, color: credit ? "var(--gain)" : "var(--ink)" }}>
                  {credit
                    ? formatSignedAmount(tx.money.amount, tx.money.currency, privacy)
                    : formatAmount(tx.money.amount.startsWith("-") ? tx.money.amount : `-${tx.money.amount}`, tx.money.currency, privacy)}
                </span>
              </button>
            );
          })}
        </div>
      )}
      <div className="muted">Card purchases can take days to settle — pending rows may change amount or disappear. Nothing here is real-time.</div>
      {openTx ? (
        <TransactionDrawer
          transaction={openTx}
          accounts={accounts}
          onClose={() => {
            setOpenId(null);
            router.replace("/transactions");
          }}
          onChanged={(next) => {
            setPage((current) =>
              current ? { ...current, items: current.items.map((item) => (item.id === next.id ? next : item)) } : current,
            );
          }}
        />
      ) : null}
    </div>
  );
}

const dateInput = {
  border: "1px solid rgba(19,26,25,.12)",
  borderRadius: 8,
  padding: "6px 8px",
  font: "500 12.5px var(--font-mono)",
  background: "transparent",
  color: "var(--ink)",
} as const;
