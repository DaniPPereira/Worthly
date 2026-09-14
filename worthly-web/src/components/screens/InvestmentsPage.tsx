"use client";

import { useEffect, useMemo, useState } from "react";
import { CurrencyTabs, EmptyState } from "@/components/ui/Primitives";
import { apiGet } from "@/lib/api";
import { useAppData } from "@/lib/app-data";
import { formatAmount, formatSignedAmount, groupByCurrency, weightPercent } from "@/lib/money";
import { formatDay, monthDateRange, monthKeyInZone } from "@/lib/period";
import type { InvestmentSummary, Position, TransactionPage } from "@/lib/types";

export function InvestmentsPage() {
  const { owner, privacy, categories } = useAppData();
  const [summary, setSummary] = useState<InvestmentSummary | null>(null);
  const [positions, setPositions] = useState<Position[]>([]);
  const [history, setHistory] = useState<TransactionPage | null>(null);
  const [currency, setCurrency] = useState(owner.reportingCurrency);

  const month = monthKeyInZone(owner.reportingTimezone);
  const { from, to } = monthDateRange(month);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      apiGet<InvestmentSummary>("/investments/summary"),
      apiGet<Position[]>("/investments/positions"),
      apiGet<TransactionPage>(`/transactions?from=${from}&to=${to}&size=20`),
    ]).then(([nextSummary, nextPositions, nextHistory]) => {
      if (cancelled) {
        return;
      }
      setSummary(nextSummary);
      setPositions(nextPositions);
      setHistory(nextHistory);
      const first = nextSummary.totalsByCurrency[0]?.currency ?? owner.reportingCurrency;
      setCurrency((current) => nextSummary.totalsByCurrency.some((row) => row.currency === current) ? current : first);
    }).catch(() => {
      if (!cancelled) {
        setSummary({ totalsByCurrency: [], observedAt: null });
        setPositions([]);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [from, owner.reportingCurrency, to]);

  const row = summary?.totalsByCurrency.find((item) => item.currency === currency);
  const currencies = summary?.totalsByCurrency.map((item) => item.currency) ?? [];
  const grouped = groupByCurrency(
    positions.filter((position) => position.marketValue),
    (position) => position.marketValue!.currency,
  );
  const visible = grouped.get(currency) ?? [];
  const portfolioTotal = visible.reduce(
    (sum, position) => sum + BigInt(toCents(position.marketValue!.amount)),
    0n,
  );

  const events = useMemo(() => {
    const dividendIds = new Set(
      categories.filter((item) => item.code?.includes("investment") || item.code === "income.investment.dividend").map((item) => item.id),
    );
    return (history?.items ?? []).filter((tx) => {
      if (tx.money.currency !== currency) {
        return false;
      }
      return (
        tx.economicType === "INVESTMENT_FUNDING" ||
        tx.economicType === "INVESTMENT_WITHDRAWAL" ||
        (tx.categoryId != null && dividendIds.has(tx.categoryId))
      );
    });
  }, [categories, currency, history]);

  if (!summary) {
    return <p className="muted">Loading investments…</p>;
  }
  if (!row) {
    return (
      <EmptyState title="No brokerage data">
        Trading 212 appears here after a read-only key is configured on the server. Worthly cannot place orders.
      </EmptyState>
    );
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <CurrencyTabs currencies={currencies} selected={currency} onSelect={setCurrency} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1.3fr", gap: 16 }}>
        <div style={{ background: "#131A19", borderRadius: 16, padding: 24, color: "var(--cream)" }}>
          <div className="label" style={{ color: "rgba(244,241,234,.68)" }}>Portfolio value</div>
          <div className="serif tabular" style={{ fontSize: 50, lineHeight: 1, marginTop: 10 }}>
            {formatAmount(row.portfolioValue, currency, privacy)}
          </div>
          <div style={{ fontSize: 12.5, color: "rgba(244,241,234,.68)", marginTop: 12 }}>
            Snapshot in {currency}. Cost basis and return are not in the v1 API.
          </div>
          <div style={{ display: "flex", gap: 28, marginTop: 24, paddingTop: 18, borderTop: "1px solid rgba(244,241,234,.14)" }}>
            <div>
              <div className="label" style={{ color: "rgba(244,241,234,.68)" }}>Free cash</div>
              <div className="mono tabular" style={{ fontSize: 15, fontWeight: 500, marginTop: 5 }}>
                {formatAmount(row.cash, currency, privacy)}
              </div>
            </div>
            <div>
              <div className="label" style={{ color: "rgba(244,241,234,.68)" }}>Positions</div>
              <div className="mono" style={{ fontSize: 15, fontWeight: 500, marginTop: 5 }}>
                {visible.length}
              </div>
            </div>
          </div>
          <div style={{ fontSize: 11, lineHeight: 1.5, color: "rgba(244,241,234,.66)", marginTop: 20 }}>
            Read through your read-only Trading 212 key, which never leaves your server. Worthly cannot place orders.
            Trading 212 Crypto is a separate account and is not in the Public API, so those balances cannot appear here.
          </div>
        </div>
        <div className="card" style={{ padding: 20 }}>
          <div className="label">Holdings in {currency}</div>
          <p className="muted" style={{ marginTop: 12 }}>
            Weights are computed within this currency only. A six-month portfolio history is not stored in v1.
          </p>
        </div>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1.5fr 1fr", gap: 16 }}>
        <div className="card" style={{ overflow: "hidden" }}>
          <div
            className="label"
            style={{
              display: "grid",
              gridTemplateColumns: "1.4fr 90px 120px 110px",
              gap: 12,
              padding: "11px 20px",
              background: "#FBF9F5",
              borderBottom: "1px solid rgba(19,26,25,.08)",
            }}
          >
            <span>Position</span>
            <span style={{ textAlign: "right" }}>Weight</span>
            <span style={{ textAlign: "right" }}>Value</span>
            <span style={{ textAlign: "right" }}>Qty</span>
          </div>
          {visible.length === 0 ? (
            <p className="muted" style={{ padding: 20 }}>No positions in {currency}.</p>
          ) : (
            visible.map((position) => (
              <div
                key={position.instrumentKey}
                style={{
                  display: "grid",
                  gridTemplateColumns: "1.4fr 90px 120px 110px",
                  gap: 12,
                  padding: "13px 20px",
                  borderBottom: "1px solid rgba(19,26,25,.05)",
                  alignItems: "center",
                }}
              >
                <span>
                  <span style={{ fontWeight: 600, fontSize: 13 }}>{position.ticker ?? position.instrumentKey}</span>
                  <span style={{ fontSize: 11.5, color: "var(--faint)", marginLeft: 9 }}>{position.instrumentKey}</span>
                </span>
                <span className="mono" style={{ textAlign: "right", fontSize: 12, color: "var(--faint)" }}>
                  {privacy ? "•••" : weightPercent(position.marketValue!.amount, fromCents(portfolioTotal))}
                </span>
                <span className="mono tabular" style={{ textAlign: "right", fontSize: 13 }}>
                  {formatAmount(position.marketValue!.amount, position.marketValue!.currency, privacy)}
                </span>
                <span className="mono" style={{ textAlign: "right", fontSize: 12.5, color: "var(--faint)" }}>
                  {privacy ? "•••" : (position.quantity ?? "—")}
                </span>
              </div>
            ))
          )}
        </div>
        <div className="card" style={{ padding: "20px 20px 8px" }}>
          <div className="label">History</div>
          {events.length === 0 ? (
            <p className="muted" style={{ marginTop: 12 }}>Funding, withdrawals and dividends from this month appear here after sync.</p>
          ) : (
            events.map((tx) => (
              <div key={tx.id} style={{ borderTop: "1px solid rgba(19,26,25,.06)", padding: "12px 0", display: "flex", alignItems: "center", gap: 12, marginTop: 10 }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, fontSize: 12.5 }}>{labelFor(tx.economicType)}</div>
                  <div style={{ fontSize: 11.5, color: "var(--faint)", marginTop: 1 }}>
                    {formatDay(tx.reportingAt, owner.reportingTimezone)} · {tx.merchant || tx.description || "Trading 212"}
                  </div>
                </div>
                <div className="mono tabular" style={{ fontSize: 12.5, color: tx.direction === "CREDIT" ? "var(--gain)" : "var(--ink)" }}>
                  {tx.direction === "CREDIT"
                    ? formatSignedAmount(tx.money.amount, tx.money.currency, privacy)
                    : formatAmount(tx.money.amount.startsWith("-") ? tx.money.amount : `-${tx.money.amount}`, tx.money.currency, privacy)}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

function toCents(amount: string): string {
  const unsigned = amount.startsWith("-") ? amount.slice(1) : amount;
  const [integer = "0", fraction = ""] = unsigned.split(".");
  return `${integer}${(fraction + "00").slice(0, 2)}`;
}

function fromCents(cents: bigint): string {
  const whole = cents / 100n;
  const frac = cents % 100n;
  return `${whole.toString()}.${frac.toString().padStart(2, "0")}`;
}

function labelFor(type: string): string {
  switch (type) {
    case "INVESTMENT_FUNDING":
      return "Deposit";
    case "INVESTMENT_WITHDRAWAL":
      return "Withdrawal";
    default:
      return type.replaceAll("_", " ");
  }
}
