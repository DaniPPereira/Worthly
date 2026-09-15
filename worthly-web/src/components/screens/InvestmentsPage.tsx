"use client";

import { useEffect, useMemo, useState } from "react";
import { CurrencyTabs, EmptyState } from "@/components/ui/Primitives";
import { apiGet } from "@/lib/api";
import { includesHoldings, isBank, useAppData } from "@/lib/app-data";
import { compareAmountDesc, formatAmount, formatSignedAmount, groupByCurrency, weightPercent } from "@/lib/money";
import { formatDay, monthDateRange, monthKeyInZone, shiftMonthKey } from "@/lib/period";
import type { InvestmentSummary, Position, TransactionPage } from "@/lib/types";

const SLICE_COLORS = ["#0E4A3E", "#2C6B5C", "#4A8878", "#C98F32", "#8A6412", "#5E6A67", "#B8BFBC", "#D9D3C7"];
const TOP_SLICES = 7;

export function InvestmentsPage() {
  const { owner, privacy, categories, connections } = useAppData();
  const [summary, setSummary] = useState<InvestmentSummary | null>(null);
  const [positions, setPositions] = useState<Position[]>([]);
  const [history, setHistory] = useState<TransactionPage | null>(null);
  const [currency, setCurrency] = useState(owner.reportingCurrency);

  const month = monthKeyInZone(owner.reportingTimezone);
  const { from } = monthDateRange(shiftMonthKey(month, -5));
  const { to } = monthDateRange(month);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      apiGet<InvestmentSummary>("/investments/summary"),
      apiGet<Position[]>("/investments/positions"),
      Promise.all([
        apiGet<TransactionPage>(`/transactions?economicType=INVESTMENT_FUNDING&from=${from}&to=${to}&size=50`),
        apiGet<TransactionPage>(`/transactions?economicType=INVESTMENT_WITHDRAWAL&from=${from}&to=${to}&size=50`),
        apiGet<TransactionPage>(`/transactions?economicType=INCOME&from=${from}&to=${to}&size=50`),
      ]).then((pages) => {
        const items = pages.flatMap((page) => page.items);
        items.sort((left, right) => right.reportingAt.localeCompare(left.reportingAt));
        return { items, page: 0, size: items.length, total: items.length } as TransactionPage;
      }),
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
  const visible = [...(grouped.get(currency) ?? [])].sort((left, right) =>
    compareAmountDesc(left.marketValue!.amount, right.marketValue!.amount),
  );
  const portfolioTotal = visible.reduce(
    (sum, position) => sum + BigInt(toCents(position.marketValue!.amount)),
    0n,
  );
  const slices = allocationSlices(visible, portfolioTotal);

  const events = useMemo(() => {
    const dividendIds = new Set(
      categories.filter((item) => item.code?.includes("investment") || item.code === "income.investment.dividend").map((item) => item.id),
    );
    return (history?.items ?? []).filter((tx) => {
      if (tx.money.currency !== currency) {
        return false;
      }
      if (tx.economicType === "INVESTMENT_FUNDING") {
        return tx.direction === "CREDIT";
      }
      if (tx.economicType === "INVESTMENT_WITHDRAWAL") {
        return tx.direction === "DEBIT";
      }
      return tx.categoryId != null && dividendIds.has(tx.categoryId);
    });
  }, [categories, currency, history]);

  if (!summary) {
    return <p className="muted">Loading investments…</p>;
  }
  if (!row) {
    const cashOnlyBrokers = connections.filter(
      (connection) =>
        isBank(connection) &&
        connection.status !== "DISABLED" &&
        (connection.brand === "TRADE_REPUBLIC" || connection.brand === "REVOLUT") &&
        !includesHoldings(connection),
    );
    const names = [
      ...new Set(
        cashOnlyBrokers
          .map((connection) => connection.institutionName)
          .filter((name): name is string => typeof name === "string" && name.length > 0),
      ),
    ];
    return (
      <EmptyState title="No brokerage data">
        {names.length > 0
          ? `${names.join(" and ")} ${names.length === 1 ? "is" : "are"} connected as a bank. Open Banking does not include holdings. Connect a brokerage with an official API from Connections.`
          : "Connect a brokerage from Connections to see holdings here. A bank connection, including Trade Republic or Revolut, is cash only. Worthly cannot place orders."}
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
            Snapshot in {currency}. Cost basis and return are not available from the broker.
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
            Read-only. Worthly cannot place orders. Crypto accounts are a separate broker product and are not in this snapshot.
          </div>
        </div>
        <div className="card" style={{ padding: 20, display: "flex", flexDirection: "column" }}>
          <div className="label">Allocation in {currency}</div>
          {visible.length === 0 ? (
            <p className="muted" style={{ marginTop: 12 }}>No holdings in this currency.</p>
          ) : (
            <div style={{ display: "flex", gap: 28, alignItems: "center", marginTop: 18, flex: 1 }}>
              <AllocationDonut slices={slices} privacy={privacy} />
              <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 8 }}>
                {slices.map((slice) => (
                  <div key={slice.label} style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <span style={{ width: 8, height: 8, borderRadius: 99, background: slice.color, flex: "none" }} />
                    <span style={{ flex: 1, fontSize: 13, fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {slice.label}
                    </span>
                    <span className="mono" style={{ fontSize: 12, color: "var(--faint)" }}>
                      {privacy ? "•••" : slice.weight}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
          <div className="muted" style={{ marginTop: 16, paddingTop: 12, borderTop: "1px solid rgba(19,26,25,.07)" }}>
            Current weights in {currency}. Price history and cost basis are not stored yet.
          </div>
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
                  <span style={{ fontWeight: 600, fontSize: 13 }}>{positionLabel(position)}</span>
                  {positionCode(position) ? (
                    <span style={{ fontSize: 11.5, color: "var(--faint)", marginLeft: 9 }}>{positionCode(position)}</span>
                  ) : null}
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
          <div className="label">Cash movements</div>
          {events.length === 0 ? (
            <p className="muted" style={{ marginTop: 12 }}>No deposits, withdrawals or dividends in the last six months.</p>
          ) : (
            events.map((tx) => (
              <div key={tx.id} style={{ borderTop: "1px solid rgba(19,26,25,.06)", padding: "12px 0", display: "flex", alignItems: "center", gap: 12, marginTop: 10 }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, fontSize: 12.5 }}>{labelFor(tx.economicType)}</div>
                  <div style={{ fontSize: 11.5, color: "var(--faint)", marginTop: 1 }}>
                    {formatDay(tx.reportingAt, owner.reportingTimezone)} · {tx.merchant || tx.description || "Broker"}
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

type Slice = { label: string; weight: string; share: number; color: string };

function allocationSlices(positions: Position[], totalCents: bigint): Slice[] {
  if (totalCents === 0n) {
    return [];
  }
  const ranked = positions.map((position) => ({
    label: positionLabel(position),
    cents: BigInt(toCents(position.marketValue!.amount)),
  }));
  const head = ranked.slice(0, TOP_SLICES);
  const tail = ranked.slice(TOP_SLICES);
  const otherCents = tail.reduce((sum, item) => sum + item.cents, 0n);
  const rows = otherCents > 0n ? [...head, { label: `Other (${tail.length})`, cents: otherCents }] : head;
  return rows.map((row, index) => ({
    label: row.label,
    weight: weightPercent(fromCents(row.cents), fromCents(totalCents)),
    share: Number(row.cents) / Number(totalCents),
    color: SLICE_COLORS[index] ?? "#B8BFBC",
  }));
}

function AllocationDonut({ slices, privacy }: { slices: Slice[]; privacy: boolean }) {
  const radius = 42;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;
  return (
    <svg width="132" height="132" viewBox="0 0 132 132" aria-hidden={privacy}>
      <circle cx="66" cy="66" r={radius} fill="none" stroke="rgba(19,26,25,.06)" strokeWidth="18" />
      {slices.map((slice) => {
        const dash = slice.share * circumference;
        const circle = (
          <circle
            key={slice.label}
            cx="66"
            cy="66"
            r={radius}
            fill="none"
            stroke={slice.color}
            strokeWidth="18"
            strokeDasharray={`${dash} ${circumference - dash}`}
            strokeDashoffset={-offset}
            transform="rotate(-90 66 66)"
            strokeLinecap="butt"
          />
        );
        offset += dash;
        return circle;
      })}
    </svg>
  );
}

function positionLabel(position: Position): string {
  const name = position.name?.trim();
  if (name) {
    return name;
  }
  return position.ticker ?? position.instrumentKey;
}

function positionCode(position: Position): string | null {
  const code = position.ticker ?? position.instrumentKey;
  const label = positionLabel(position);
  return code && code !== label ? code : null;
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
    case "INCOME":
      return "Dividend";
    default:
      return type.replaceAll("_", " ");
  }
}
