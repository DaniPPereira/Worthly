"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { CurrencyTabs, EmptyState, MonthNav } from "@/components/ui/Primitives";
import { apiGet } from "@/lib/api";
import { cashAccountCount, connectionLabel, includesHoldings, isBank, useAppData } from "@/lib/app-data";
import { areaPath, linePath, toChartNumber } from "@/lib/chart";
import { addAmounts, formatAmount, formatRate, formatSignedAmount, isNegative } from "@/lib/money";
import { formatDay, formatMonthLabel, monthDateRange, monthKeyInZone, monthKeysThrough } from "@/lib/period";
import { transactionsHref } from "@/lib/transactions-href";
import type { Account, InvestmentSummary, MonthlyAnalytics, TransactionPage, WealthSummary } from "@/lib/types";

const CAT_COLORS = ["#0E4A3E", "#2C6B5C", "#4A8878", "#C98F32", "#B8BFBC"];

type ExpenseRow = { name: string; amount: string; currency: string; categoryId: string };

export function DashboardPage() {
  const { owner, privacy, connections, categories } = useAppData();
  const [wealth, setWealth] = useState<WealthSummary | null>(null);
  const [investments, setInvestments] = useState<InvestmentSummary | null>(null);
  const [months, setMonths] = useState<MonthlyAnalytics[]>([]);
  const [recent, setRecent] = useState<TransactionPage | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [expenses, setExpenses] = useState<TransactionPage | null>(null);
  const [currency, setCurrency] = useState<string>(owner.reportingCurrency);
  const [loadError, setLoadError] = useState<string | null>(null);
  const currentMonth = monthKeyInZone(owner.reportingTimezone);
  const [month, setMonth] = useState(currentMonth);

  const keys = useMemo(() => monthKeysThrough(month, 6), [month]);
  const { from, to } = monthDateRange(month);

  useEffect(() => {
    let cancelled = false;
    void apiGet<WealthSummary>("/analytics/summary")
      .then((nextWealth) => {
        if (cancelled) {
          return;
        }
        setWealth(nextWealth);
        const first = nextWealth.totalsByCurrency[0]?.currency ?? owner.reportingCurrency;
        setCurrency((current) =>
          nextWealth.totalsByCurrency.some((row) => row.currency === current) ? current : first,
        );
      })
      .catch(() => {
        if (!cancelled) {
          setLoadError("Dashboard totals are unavailable.");
        }
      });
    Promise.all([
      apiGet<InvestmentSummary>("/investments/summary").catch(() => ({ totalsByCurrency: [], observedAt: null })),
      apiGet<TransactionPage>("/transactions?size=5"),
      apiGet<Account[]>("/accounts"),
    ])
      .then(([nextInvestments, nextRecent, nextAccounts]) => {
        if (cancelled) {
          return;
        }
        setInvestments(nextInvestments);
        setRecent(nextRecent);
        setAccounts(nextAccounts);
      })
      .catch(() => {
        if (!cancelled) {
          setLoadError((current) => current ?? "Dashboard totals are unavailable.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [owner.reportingCurrency]);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      Promise.all(keys.map((key) => apiGet<MonthlyAnalytics>(`/analytics/monthly?month=${key}`))),
      apiGet<TransactionPage>(`/transactions?economicType=EXPENSE&from=${from}&to=${to}&size=200`),
    ])
      .then(([nextMonths, nextExpenses]) => {
        if (cancelled) {
          return;
        }
        setMonths(nextMonths);
        setExpenses(nextExpenses);
      })
      .catch(() => {
        if (!cancelled) {
          setLoadError((current) => current ?? "Dashboard totals are unavailable.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [from, keys, to]);

  const currencies = wealth?.totalsByCurrency.map((row) => row.currency) ?? [];
  const wealthRow = wealth?.totalsByCurrency.find((row) => row.currency === currency);
  const investmentRow = investments?.totalsByCurrency.find((row) => row.currency === currency);
  const brokerageCash = investmentRow?.cash ?? "0.00";
  const cashAvailable = wealthRow ? addAmounts(wealthRow.liquidCash, brokerageCash) : "0.00";
  const portfolioValue = investmentRow?.portfolioValue ?? "0.00";
  const monthlyNow = months.find((item) => item.month === month)?.totalsByCurrency.find((row) => row.currency === currency);
  const seriesFor = (pick: (row: NonNullable<typeof monthlyNow>) => string | null) =>
    keys.map((key) => {
      const row = months.find((item) => item.month === key)?.totalsByCurrency.find((entry) => entry.currency === currency);
      const amount = row ? pick(row) : "0";
      return amount ? toChartNumber(amount) : 0;
    });
  const savingsSeries = seriesFor((row) => row.savings);
  const incomeSeries = seriesFor((row) => row.income);
  const expenseSeries = seriesFor((row) => row.expenses);
  const investedSeries = seriesFor((row) => row.invested);
  const rateSeries = seriesFor((row) => row.savingsRate);

  const categoryRows = useMemo(() => {
    const byId = new Map(categories.map((category) => [category.id, category]));
    const uncategorizedId = categories.find((item) => item.code === "uncategorized")?.id ?? "uncategorized";
    const totals = new Map<string, { name: string; categoryId: string; cents: bigint }>();
    for (const tx of expenses?.items ?? []) {
      if (tx.money.currency !== currency) {
        continue;
      }
      const category = tx.categoryId ? byId.get(tx.categoryId) : undefined;
      const categoryId = tx.categoryId ?? uncategorizedId;
      const name = category?.label ?? "Uncategorized";
      const unsigned = tx.money.amount.startsWith("-") ? tx.money.amount.slice(1) : tx.money.amount;
      const [integer = "0", fraction = ""] = unsigned.split(".");
      const mag = BigInt(integer) * 100n + BigInt((fraction + "00").slice(0, 2));
      const existing = totals.get(categoryId);
      if (existing) {
        existing.cents += mag;
      } else {
        totals.set(categoryId, { name, categoryId, cents: mag });
      }
    }
    const rows: ExpenseRow[] = [...totals.values()]
      .sort((a, b) => (a.cents === b.cents ? 0 : a.cents > b.cents ? -1 : 1))
      .slice(0, 5)
      .map((row) => ({
        name: row.name,
        categoryId: row.categoryId,
        currency,
        amount: `${(row.cents / 100n).toString()}.${(row.cents % 100n).toString().padStart(2, "0")}`,
      }));
    return rows;
  }, [categories, currency, expenses]);

  const maxCat = categoryRows[0]?.amount ?? "0";
  const cashAccounts = cashAccountCount(accounts, currency);
  const reauthConnection = connections.find((connection) => connection.status === "REAUTH_REQUIRED");
  const hasBank = connections.some((connection) => isBank(connection));
  const showInvestments =
    connections.some((connection) => includesHoldings(connection))
    || isNonZeroAmount(portfolioValue)
    || isNonZeroAmount(brokerageCash)
    || months.some((item) => isNonZeroAmount(item.totalsByCurrency.find((row) => row.currency === currency)?.invested));
  const monthMetrics = [
    {
      label: `Income · ${formatMonthLabel(month)}`,
      value: monthlyNow ? formatAmount(monthlyNow.income, currency, privacy) : "—",
      note: month === currentMonth ? "This reporting month" : formatMonthLabel(month),
      fg: "var(--gain)",
      spark: linePath(incomeSeries, 120, 26, 4),
    },
    {
      label: `Expenses · ${formatMonthLabel(month)}`,
      value: monthlyNow ? formatAmount(monthlyNow.expenses, currency, privacy) : "—",
      note: "Purchases and fees · transfers excluded",
      fg: "var(--loss)",
      spark: linePath(expenseSeries, 120, 26, 4),
    },
    ...(showInvestments
      ? [
          {
            label: `Invested · ${formatMonthLabel(month)}`,
            value: monthlyNow ? formatAmount(monthlyNow.invested, currency, privacy) : "—",
            note: "Cash moved into investments",
            fg: "var(--brass)",
            spark: linePath(investedSeries, 120, 26, 4),
          },
        ]
      : []),
    {
      label: "Savings rate",
      value: monthlyNow ? formatRate(monthlyNow.savingsRate, privacy) : "—",
      note:
        monthlyNow?.savingsRateReason === "NO_POSITIVE_INCOME"
          ? "No income this month"
          : "Share of income kept after expenses",
      fg: "var(--pine)",
      spark: linePath(rateSeries, 120, 26, 4),
    },
  ];
  const connectedBank = connections.find(
    (connection) =>
      isBank(connection) && (connection.status === "ACTIVE" || connection.status === "ERROR"),
  );
  const needsReauth = connections.some((connection) => connection.status === "REAUTH_REQUIRED");

  if (loadError) {
    return <EmptyState title="Dashboard unavailable">{loadError}</EmptyState>;
  }
  if (!wealth) {
    return <p className="muted">Loading dashboard…</p>;
  }
  if (!wealthRow) {
    if (needsReauth) {
      return (
        <EmptyState title="Bank access needs a refresh">
          Open{" "}
          <Link href="/connections" style={{ fontWeight: 600, color: "var(--pine)" }}>
            Connections
          </Link>{" "}
          and reauthorize. Worthly cannot read balances until the bank session is valid again.
        </EmptyState>
      );
    }
    if (connectedBank) {
      return (
        <EmptyState title="Waiting for the first balance sync">
          {connectedBank.institutionName ?? "Your bank"} is connected, but Worthly has not stored balances yet. Open{" "}
          <Link href="/connections" style={{ fontWeight: 600, color: "var(--pine)" }}>
            Connections
          </Link>{" "}
          and tap Sync now. If that fails, disconnect and reconnect the bank.
        </EmptyState>
      );
    }
    return <EmptyState title="No balances yet">Connect a bank from Connections to see cash and spending. Totals stay grouped by currency.</EmptyState>;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <CurrencyTabs currencies={currencies} selected={currency} onSelect={setCurrency} />
        <div style={{ marginLeft: "auto" }}>
          <MonthNav month={month} currentMonth={currentMonth} onChange={setMonth} />
        </div>
      </div>
      {reauthConnection ? (
        <div style={{ background: "#FBF3E4", border: "1px solid rgba(138,100,18,.3)", borderRadius: 12, padding: "13px 16px", display: "flex", alignItems: "center", gap: 12 }}>
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="#8A6412" strokeWidth="1.8" strokeLinecap="round">
            <path d="M12 9v4M12 17h.01" />
            <path d="M10.3 3.9 2.4 17.4A1.9 1.9 0 0 0 4 20.3h16a1.9 1.9 0 0 0 1.6-2.9L13.7 3.9a1.9 1.9 0 0 0-3.4 0z" />
          </svg>
          <div style={{ flex: 1 }}>
            <span style={{ fontWeight: 600, fontSize: 13 }}>{connectionLabel(reauthConnection)} needs reauthorization.</span>{" "}
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
            {formatAmount(cashAvailable, currency, privacy)}
          </div>
          <div style={{ fontSize: 12.5, color: "rgba(244,241,234,.62)", marginTop: 8 }}>
            {cashAccounts} account{cashAccounts === 1 ? "" : "s"} · {currency}
          </div>
          <div style={{ display: "flex", gap: 28, marginTop: "auto", paddingTop: 22, borderTop: "1px solid rgba(244,241,234,.16)" }}>
            <div>
              <div className="label" style={{ color: "rgba(244,241,234,.62)" }}>Net worth</div>
              <div className="serif tabular" style={{ fontSize: 27, lineHeight: 1.1, marginTop: 5 }}>
                {formatAmount(wealthRow.netWorth, currency, privacy)}
              </div>
            </div>
            <div>
              <div className="label" style={{ color: "rgba(244,241,234,.62)" }}>
                {month === currentMonth ? "This month" : formatMonthLabel(month)}
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 7, marginTop: 9 }}>
                <svg
                  width="11"
                  height="11"
                  viewBox="0 0 24 24"
                  fill={monthlyNow && isNegative(monthlyNow.savings) ? "#E8A090" : "#8FD0B4"}
                  style={{ transform: monthlyNow && isNegative(monthlyNow.savings) ? "rotate(180deg)" : undefined }}
                >
                  <path d="M12 5l7 12H5z" />
                </svg>
                <span className="mono tabular" style={{ fontSize: 14, color: monthlyNow && isNegative(monthlyNow.savings) ? "#E8A090" : "#C6E7D6" }}>
                  {monthlyNow ? formatSignedAmount(monthlyNow.savings, currency, privacy) : "—"}
                </span>
              </div>
            </div>
            {showInvestments ? (
              <div>
                <div className="label" style={{ color: "rgba(244,241,234,.62)" }}>Investments</div>
                <div className="serif tabular" style={{ fontSize: 27, lineHeight: 1.1, marginTop: 5, color: "#E8C382" }}>
                  {formatAmount(portfolioValue, currency, privacy)}
                </div>
              </div>
            ) : null}
          </div>
        </div>
        <div className="card" style={{ padding: 20 }}>
          <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between" }}>
            <div className="label">Savings · 6 months</div>
            <div className="mono" style={{ fontSize: 11.5, color: "var(--gain)" }}>
              {currency}
            </div>
          </div>
          <svg viewBox="0 0 460 150" preserveAspectRatio="none" width="100%" height="150" style={{ marginTop: 14, display: "block", overflow: "visible" }} role="img" aria-label="Monthly savings for the selected currency">
            <path d="M0 130h460M0 95h460M0 60h460M0 25h460" stroke="rgba(19,26,25,.06)" strokeWidth="1" />
            <path d={areaPath(savingsSeries, 460, 150, 12, "zero")} fill="rgba(14,74,62,.09)" />
            <path d={linePath(savingsSeries, 460, 150, 12, "zero")} fill="none" stroke="#0E4A3E" strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round" />
          </svg>
          <div className="mono" style={{ display: "grid", gridTemplateColumns: `repeat(${keys.length}, minmax(0, 1fr))`, fontSize: 10, color: "var(--faint)", marginTop: 6 }}>
            {keys.map((key) => (
              <span key={key} style={{ textAlign: "center" }}>{formatMonthLabel(key)}</span>
            ))}
          </div>
          <div className="muted" style={{ marginTop: 12, paddingTop: 11, borderTop: "1px solid rgba(19,26,25,.07)" }}>
            Income minus spending each month, in {currency}. Transfers between your own accounts are excluded.
          </div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: `repeat(${monthMetrics.length}, minmax(0, 1fr))`, gap: 16 }}>
        {monthMetrics.map((metric) => (
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
        {categoryRows.length > 0 || hasBank ? (
        <div className="card" style={{ padding: 20 }}>
          <div className="label">Where it went · {formatMonthLabel(month)}</div>
          <div style={{ marginTop: 16, display: "flex", flexDirection: "column", gap: 13 }}>
            {categoryRows.length === 0 ? (
              hasBank ? <p className="muted">No expenses in {currency} this month.</p> : null
            ) : (
              categoryRows.map((row, index) => (
                <Link
                  key={row.categoryId}
                  href={transactionsHref({ from, to, categoryId: row.categoryId, economicType: "EXPENSE" })}
                  style={{ display: "block", textDecoration: "none", color: "inherit", borderRadius: 8, padding: "2px 0" }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                    <span style={{ fontWeight: 500, fontSize: 13 }}>{row.name}</span>
                    <span className="mono tabular" style={{ fontSize: 12.5, color: "#3E4A47" }}>
                      {formatAmount(row.amount, currency, privacy)}
                    </span>
                  </div>
                  <div style={{ height: 6, borderRadius: 3, background: "rgba(19,26,25,.07)", marginTop: 6, overflow: "hidden" }}>
                    <div style={{ height: 6, borderRadius: 3, background: CAT_COLORS[index] ?? "#B8BFBC", width: barWidth(row.amount, maxCat) }} />
                  </div>
                </Link>
              ))
            )}
          </div>
          {categoryRows.length > 0 ? (
          <div className="muted" style={{ marginTop: 16, paddingTop: 12, borderTop: "1px solid rgba(19,26,25,.07)" }}>
            Click a category to see those expenses. Internal transfers are excluded. Bars are relative to the largest category in {currency}.
          </div>
          ) : null}
        </div>
        ) : null}
        {(recent?.items ?? []).length > 0 ? (
        <div className="card" style={{ padding: "20px 20px 8px" }}>
          <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between" }}>
            <div className="label">Recent movements</div>
            <Link href="/transactions" style={{ fontWeight: 600, fontSize: 12, color: "var(--pine)", textDecoration: "none" }}>
              All transactions →
            </Link>
          </div>
          <div style={{ marginTop: 12 }}>
            {(recent?.items ?? []).map((tx) => {
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
                    <span style={{ flex: 1, minWidth: 0 }}>
                      <span style={{ display: "block", fontWeight: 600, fontSize: 13, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {tx.merchant || tx.description || "Transaction"}
                      </span>
                      <span style={{ display: "block", fontSize: 11.5, color: "var(--faint)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {[category?.label ?? "Uncategorized", tx.location].filter(Boolean).join(" · ")}
                      </span>
                    </span>
                    <span className="mono tabular" style={{ width: 110, flex: "none", textAlign: "right", fontSize: 13, color: credit ? "var(--gain)" : "var(--ink)" }}>
                      {credit ? formatSignedAmount(tx.money.amount, tx.money.currency, privacy) : formatAmount(tx.money.amount.startsWith("-") ? tx.money.amount : `-${tx.money.amount}`, tx.money.currency, privacy)}
                    </span>
                  </Link>
                );
              })}
          </div>
        </div>
        ) : null}
      </div>
    </div>
  );
}

function isNonZeroAmount(amount: string | null | undefined): boolean {
  if (!amount) {
    return false;
  }
  return addAmounts(amount, "0.00") !== "0.00";
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
