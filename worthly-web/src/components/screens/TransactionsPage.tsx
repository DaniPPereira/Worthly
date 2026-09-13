"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { TransactionDrawer } from "@/components/screens/TransactionDrawer";
import { EmptyState } from "@/components/ui/Primitives";
import { apiGet, downloadCsv } from "@/lib/api";
import { useAppData } from "@/lib/app-data";
import { formatAmount, formatSignedAmount } from "@/lib/money";
import { formatDay, monthDateRange, monthKeyInZone } from "@/lib/period";
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
  const { owner, privacy, categories } = useAppData();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [filter, setFilter] = useState<Filter>("All");
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");
  const [page, setPage] = useState<TransactionPage | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [openId, setOpenId] = useState<string | null>(searchParams.get("open"));

  const month = monthKeyInZone(owner.reportingTimezone);
  const { from, to } = monthDateRange(month);
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
        <div className="mono" style={{ background: "#fff", border: "1px solid rgba(19,26,25,.11)", borderRadius: 10, padding: "10px 13px", fontSize: 12.5 }}>
          {from.slice(8)} {monthLabel(from)} – {to.slice(8)} {monthLabel(to)} {to.slice(0, 4)}
        </div>
        <button
          type="button"
          className="btn btn-ghost"
          style={{ height: 40 }}
          onClick={() => void downloadCsv(`/exports/transactions.csv?from=${from}&to=${to}`, "transactions.csv")}
        >
          Export CSV
        </button>
      </div>
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
        <EmptyState title="No transactions in this view">Try another filter or wait for the next successful sync.</EmptyState>
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
                  <span style={{ fontWeight: 600, fontSize: 13, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                    {tx.merchant || tx.description || "Transaction"}
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

function monthLabel(isoDate: string): string {
  return new Intl.DateTimeFormat("en-GB", { month: "short" }).format(new Date(`${isoDate}T00:00:00Z`));
}
