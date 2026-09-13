"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { CurrencyTabs, EmptyState } from "@/components/ui/Primitives";
import { apiGet } from "@/lib/api";
import { useAppData } from "@/lib/app-data";
import { areaPath, linePath, toChartNumber } from "@/lib/chart";
import { formatAmount, formatRate, formatSignedAmount } from "@/lib/money";
import { formatDay, formatMonthLabel, monthDateRange, monthKeyInZone, previousMonthKeys } from "@/lib/period";
import type { Account, MonthlyAnalytics, TransactionPage, WealthSummary } from "@/lib/types";

const CAT_COLORS = ["#0E4A3E", "#2C6B5C", "#4A8878", "#C98F32", "#B8BFBC"];

type ExpenseRow = { name: string; amount: string; currency: string };

export function DashboardPage() {
  const { owner, privacy, connections, notifications, categories } = useAppData();
  const [wealth, setWealth] = useState<WealthSummary | null>(null);
  const [months, setMonths] = useState<MonthlyAnalytics[]>([]);
  const [recent, setRecent] = useState<TransactionPage | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [expenses, setExpenses] = useState<TransactionPage | null>(null);
  const [currency, setCurrency] = useState<string>(owner.reportingCurrency);
  const [loadError, setLoadError] = useState<string | null>(null);

  const month = monthKeyInZone(owner.reportingTimezone);
  const keys = useMemo(() => previousMonthKeys(owner.reportingTimezone, 6), [owner.reportingTimezone]);
  const { from, to } = monthDateRange(month);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      apiGet<WealthSummary>("/analytics/summary"),
      Promise.all(keys.map((key) => apiGet<MonthlyAnalytics>(`/analytics/monthly?month=${key}`))),
      apiGet<TransactionPage>("/transactions?size=5"),
      apiGet<Account[]>("/accounts"),
      apiGet<TransactionPage>(`/transactions?economicType=EXPENSE&from=${from}&to=${to}&size=200`),
    ])
      .then(([nextWealth, nextMonths, nextRecent, nextAccounts, nextExpenses]) => {
        if (cancelled) {
          return;
        }
        setWealth(nextWealth);
        setMonths(nextMonths);
        setRecent(nextRecent);
        setAccounts(nextAccounts);
        setExpenses(nextExpenses);
        const first = nextWealth.totalsByCurrency[0]?.currency ?? owner.reportingCurrency;
        setCurrency((current) => nextWealth.totalsByCurrency.some((row) => row.currency === current) ? current : first);
      })
      .catch(() => {
        if (!cancelled) {
          setLoadError("Dashboard totals are unavailable.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [from, keys, owner.reportingCurrency, to]);

  const currencies = wealth?.totalsByCurrency.map((row) => row.currency) ?? [];
  const wealthRow = wealth?.totalsByCurrency.find((row) => row.currency === currency);
  const monthlyNow = months.find((item) => item.month === month)?.totalsByCurrency.find((row) => row.currency === currency);
  const savingsSeries = months.map((item) => {
    const row = item.totalsByCurrency.find((entry) => entry.currency === currency);
    return row ? toChartNumber(row.savings) : 0;
  });
  const incomeSeries = months.map((item) => toChartNumber(item.totalsByCurrency.find((row) => row.currency === currency)?.income ?? "0"));
  const expenseSeries = months.map((item) => toChartNumber(item.totalsByCurrency.find((row) => row.currency === currency)?.expenses ?? "0"));
  const investedSeries = months.map((item) => toChartNumber(item.totalsByCurrency.find((row) => row.currency === currency)?.invested ?? "0"));
  const rateSeries = months.map((item) => {
    const rate = item.totalsByCurrency.find((row) => row.currency === currency)?.savingsRate;
    return rate ? toChartNumber(rate) : 0;
  });

  const categoryRows = useMemo(() => {
    const byId = new Map(categories.map((category) => [category.id, category]));
    const totals = new Map<string, { name: string; cents: bigint }>();
    for (const tx of expenses?.items ?? []) {
      if (tx.money.currency !== currency) {
        continue;
      }
      const category = tx.categoryId ? byId.get(tx.categoryId) : undefined;
      const name = category?.label ?? "Uncategorized";
      const unsigned = tx.money.amount.startsWith("-") ? tx.money.amount.slice(1) : tx.money.amount;
      const [integer = "0", fraction = ""] = unsigned.split(".");
      const mag = BigInt(integer) * 100n + BigInt((fraction + "00").slice(0, 2));
      const existing = totals.get(name);
      if (existing) {
        existing.cents += mag;
      } else {
        totals.set(name, { name, cents: mag });
      }
    }
    const rows: ExpenseRow[] = [...totals.values()]
      .sort((a, b) => (a.cents === b.cents ? 0 : a.cents > b.cents ? -1 : 1))
      .slice(0, 5)
      .map((row) => ({
        name: row.name,
        currency,
        amount: `${(row.cents / 100n).toString()}.${(row.cents % 100n).toString().padStart(2, "0")}`,
      }));
    return rows;
  }, [categories, currency, expenses]);

  const maxCat = categoryRows[0]?.amount ?? "0";
  const liquidAccounts = accounts.filter((account) => account.includedInLiquidCash && account.currency === currency);
  const providers = new Set(liquidAccounts.map((account) => account.provider)).size;
  const reauth = notifications.find((item) => item.readAt == null && item.type === "CONNECTION_REAUTH_REQUIRED");
  const reauthConnection = connections.find((connection) => connection.status === "REAUTH_REQUIRED");

  if (loadError) {
    return <EmptyState title="Dashboard unavailable">{loadError}</EmptyState>;
  }
  if (!wealth) {
    return <p className="muted">Loading dashboard…</p>;
  }
  if (!wealthRow) {
    return <EmptyState title="No balances yet">Connect a bank or Trading 212 to see cash and net worth. Totals stay grouped by currency.</EmptyState>;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <CurrencyTabs currencies={currencies} selected={currency} onSelect={setCurrency} />
      {reauth ? (
        <div style={{ background: "#FBF3E4", border: "1px solid rgba(138,100,18,.3)", borderRadius: 12, padding: "13px 16px", display: "flex", alignItems: "center", gap: 12 }}>
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="#8A6412" strokeWidth="1.8" strokeLinecap="round">
            <path d="M12 9v4M12 17h.01" />
            <path d="M10.3 3.9 2.4 17.4A1.9 1.9 0 0 0 4 20.3h16a1.9 1.9 0 0 0 1.6-2.9L13.7 3.9a1.9 1.9 0 0 0-3.4 0z" />
          </svg>
          <div style={{ flex: 1 }}>
            <span style={{ fontWeight: 600, fontSize: 13 }}>{reauthConnection?.institutionName ?? "A bank"} needs reauthorization.</span>{" "}
            <span style={{ fontSize: 12.5, color: "#6E5A2E" }}>Open Banking consent expired — its balances below may be stale.</span>
          </div>
          <Link href="/connections" className="btn btn-ghost" style={{ height: 32, textDecoration: "none" }}>
            Review connection
          </Link>
        </div>
      ) : null}

      <div style={{ display: "grid", gridTemplateColumns: "1.35fr 1fr", gap: 16 }}>
        <div style={{ background: "var(--pine)", borderRadius: 16, padding: 24, color: "var(--cream)", display: "flex", flexDirection: "column" }}>
          <div className="label" style={{ color: "rgba(244,241,234,.62)" }}>Cash available now</div>
          <div className="serif tabular" style={{ fontSize: 56, lineHeight: 1, marginTop: 10 }}>
            {formatAmount(wealthRow.liquidCash, currency, privacy)}
          </div>
          <div style={{ fontSize: 12.5, color: "rgba(244,241,234,.62)", marginTop: 8 }}>
            Across {liquidAccounts.length} account{liquidAccounts.length === 1 ? "" : "s"} · {providers} provider{providers === 1 ? "" : "s"} · excludes investments · {currency}
          </div>
          <div style={{ display: "flex", gap: 28, marginTop: "auto", paddingTop: 22, borderTop: "1px solid rgba(244,241,234,.16)" }}>
            <div>
              <div className="label" style={{ color: "rgba(244,241,234,.62)" }}>Net worth</div>
              <div className="serif tabular" style={{ fontSize: 27, lineHeight: 1.1, marginTop: 5 }}>
                {formatAmount(wealthRow.netWorth, currency, privacy)}
              </div>
            </div>
            <div>
              <div className="label" style={{ color: "rgba(244,241,234,.62)" }}>This month</div>
              <div style={{ display: "flex", alignItems: "center", gap: 7, marginTop: 9 }}>
                <svg width="11" height="11" viewBox="0 0 24 24" fill="#8FD0B4">
                  <path d="M12 5l7 12H5z" />
                </svg>
                <span className="mono tabular" style={{ fontSize: 14, color: "#C6E7D6" }}>
                  {monthlyNow ? formatSignedAmount(monthlyNow.savings, currency, privacy) : "—"}
                </span>
              </div>
            </div>
            <div>
              <div className="label" style={{ color: "rgba(244,241,234,.62)" }}>Invested</div>
              <div className="serif tabular" style={{ fontSize: 27, lineHeight: 1.1, marginTop: 5, color: "#E8C382" }}>
                {formatAmount(wealthRow.investmentValue, currency, privacy)}
              </div>
            </div>
          </div>
        </div>
        <div className="card" style={{ padding: 20 }}>
          <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between" }}>
            <div className="label">Savings · 6 months</div>
            <div className="mono" style={{ fontSize: 11.5, color: "var(--gain)" }}>
              {currency}
            </div>
          </div>
          <svg viewBox="0 0 460 150" width="100%" height="150" style={{ marginTop: 14, display: "block" }} role="img" aria-label="Monthly savings for the selected currency">
            <path d="M0 130h460M0 95h460M0 60h460M0 25h460" stroke="rgba(19,26,25,.06)" strokeWidth="1" />
            <path d={areaPath(savingsSeries, 460, 150, 12)} fill="rgba(14,74,62,.09)" />
            <path d={linePath(savingsSeries, 460, 150, 12)} fill="none" stroke="#0E4A3E" strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round" />
          </svg>
          <div className="mono" style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "var(--faint)", marginTop: 6 }}>
            {keys.map((key) => (
              <span key={key}>{formatMonthLabel(key)}</span>
            ))}
          </div>
          <div className="muted" style={{ marginTop: 12, paddingTop: 11, borderTop: "1px solid rgba(19,26,25,.07)" }}>
            Historical net worth is not stored in v1. This chart is monthly savings in {currency} only — currencies are never added together.
          </div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 16 }}>
        {[
          { label: `Income · ${formatMonthLabel(month)}`, value: monthlyNow ? formatAmount(monthlyNow.income, currency, privacy) : "—", note: "This reporting month", fg: "var(--gain)", spark: linePath(incomeSeries, 120, 26, 4) },
          { label: `Expenses · ${formatMonthLabel(month)}`, value: monthlyNow ? formatAmount(monthlyNow.expenses, currency, privacy) : "—", note: "Transfers excluded", fg: "var(--loss)", spark: linePath(expenseSeries, 120, 26, 4) },
          { label: `Invested · ${formatMonthLabel(month)}`, value: monthlyNow ? formatAmount(monthlyNow.invested, currency, privacy) : "—", note: "Funding, not spending", fg: "var(--brass)", spark: linePath(investedSeries, 120, 26, 4) },
          { label: "Savings rate", value: monthlyNow ? formatRate(monthlyNow.savingsRate, privacy) : "—", note: monthlyNow?.savingsRateReason ?? "Income minus expenses", fg: "var(--pine)", spark: linePath(rateSeries, 120, 26, 4) },
        ].map((metric) => (
          <div key={metric.label} className="card" style={{ padding: "16px 18px", borderRadius: 14 }}>
            <div className="label">{metric.label}</div>
            <div className="serif tabular" style={{ fontSize: 28, lineHeight: 1.1, color: metric.fg, marginTop: 8 }}>
              {metric.value}
            </div>
            <svg viewBox="0 0 120 26" width="100%" height="26" style={{ marginTop: 10, display: "block" }} aria-hidden="true">
              <path d={metric.spark} fill="none" stroke={metric.fg} strokeWidth="1.8" strokeLinejoin="round" opacity=".5" />
            </svg>
            <div style={{ fontSize: 11, color: "var(--faint)", marginTop: 5 }}>{metric.note}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1.35fr", gap: 16 }}>
        <div className="card" style={{ padding: 20 }}>
          <div className="label">Where it went · {formatMonthLabel(month)}</div>
          <div style={{ marginTop: 16, display: "flex", flexDirection: "column", gap: 13 }}>
            {categoryRows.length === 0 ? (
              <p className="muted">No expenses in {currency} this month.</p>
            ) : (
              categoryRows.map((row, index) => (
                <div key={row.name}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                    <span style={{ fontWeight: 500, fontSize: 13 }}>{row.name}</span>
                    <span className="mono tabular" style={{ fontSize: 12.5, color: "#3E4A47" }}>
                      {formatAmount(row.amount, currency, privacy)}
                    </span>
                  </div>
                  <div style={{ height: 6, borderRadius: 3, background: "rgba(19,26,25,.07)", marginTop: 6, overflow: "hidden" }}>
                    <div style={{ height: 6, borderRadius: 3, background: CAT_COLORS[index] ?? "#B8BFBC", width: barWidth(row.amount, maxCat) }} />
                  </div>
                </div>
              ))
            )}
          </div>
          <div className="muted" style={{ marginTop: 16, paddingTop: 12, borderTop: "1px solid rgba(19,26,25,.07)" }}>
            Internal transfers are excluded from this list. Percent bars are relative to the largest category in {currency}.
          </div>
        </div>
        <div className="card" style={{ padding: "20px 20px 8px" }}>
          <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between" }}>
            <div className="label">Recent movements</div>
            <Link href="/transactions" style={{ fontWeight: 600, fontSize: 12, color: "var(--pine)", textDecoration: "none" }}>
              All transactions →
            </Link>
          </div>
          <div style={{ marginTop: 12 }}>
            {(recent?.items ?? []).length === 0 ? (
              <p className="muted">No transactions imported yet.</p>
            ) : (
              (recent?.items ?? []).map((tx) => {
                const category = categories.find((item) => item.id === tx.categoryId);
                const credit = tx.direction === "CREDIT";
                return (
                  <Link
                    key={tx.id}
                    href={`/transactions?open=${tx.id}`}
                    style={{
                      width: "100%",
                      textAlign: "left",
                      textDecoration: "none",
                      color: "inherit",
                      borderTop: "1px solid rgba(19,26,25,.06)",
                      padding: "11px 0",
                      display: "flex",
                      alignItems: "center",
                      gap: 14,
                    }}
                  >
                    <span className="mono" style={{ width: 82, flex: "none", fontSize: 11.5, color: "var(--faint)" }}>
                      {formatDay(tx.reportingAt, owner.reportingTimezone)}
                    </span>
                    <span style={{ flex: 1, minWidth: 0, fontWeight: 600, fontSize: 13, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                      {tx.merchant || tx.description || "Transaction"}
                    </span>
                    <span style={{ width: 140, flex: "none", fontSize: 12, color: "var(--faint)" }}>{category?.label ?? "Uncategorized"}</span>
                    <span className="mono tabular" style={{ width: 110, flex: "none", textAlign: "right", fontSize: 13, color: credit ? "var(--gain)" : "var(--ink)" }}>
                      {credit ? formatSignedAmount(tx.money.amount, tx.money.currency, privacy) : formatAmount(tx.money.amount.startsWith("-") ? tx.money.amount : `-${tx.money.amount}`, tx.money.currency, privacy)}
                    </span>
                  </Link>
                );
              })
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function barWidth(part: string, total: string): string {
  const cents = (amount: string) => {
    const unsigned = amount.startsWith("-") ? amount.slice(1) : amount;
    const [integer = "0", fraction = ""] = unsigned.split(".");
    return BigInt(integer) * 100n + BigInt((fraction + "00").slice(0, 2));
  };
  const denom = cents(total);
  if (denom === 0n) {
    return "0%";
  }
  return `${((cents(part) * 100n) / denom).toString()}%`;
}
