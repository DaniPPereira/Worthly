import type { ReactNode } from "react";
import { formatMonthTitle, shiftMonthKey } from "@/lib/period";

export function CurrencyTabs({
  currencies,
  selected,
  onSelect,
}: {
  currencies: string[];
  selected: string;
  onSelect: (currency: string) => void;
}) {
  if (currencies.length <= 1) {
    return null;
  }
  return (
    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
      {currencies.map((currency) => {
        const active = currency === selected;
        return (
          <button
            key={currency}
            type="button"
            className="btn"
            onClick={() => onSelect(currency)}
            style={{
              height: 32,
              background: active ? "var(--pine)" : "var(--white)",
              color: active ? "var(--cream)" : "var(--ink)",
              borderColor: active ? "var(--pine)" : "rgba(19,26,25,.12)",
              fontFamily: "var(--font-mono)",
              fontWeight: 500,
              fontSize: 12,
            }}
          >
            {currency}
          </button>
        );
      })}
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="card" style={{ padding: 24 }}>
      <div style={{ fontWeight: 600 }}>{title}</div>
      <p className="muted" style={{ margin: "8px 0 0" }}>
        {children}
      </p>
    </div>
  );
}

export function MonthNav({
  month,
  currentMonth,
  onChange,
}: {
  month: string;
  currentMonth: string;
  onChange: (monthKey: string) => void;
}) {
  const atLatest = month >= currentMonth;
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 6,
        background: "#fff",
        border: "1px solid rgba(19,26,25,.11)",
        borderRadius: 10,
        padding: "4px 8px",
      }}
    >
      <button
        type="button"
        className="btn btn-ghost"
        style={{ height: 32, width: 32, padding: 0, border: "none" }}
        aria-label="Previous month"
        onClick={() => onChange(shiftMonthKey(month, -1))}
      >
        ‹
      </button>
      <span style={{ minWidth: 148, textAlign: "center", fontWeight: 600, fontSize: 13.5 }}>{formatMonthTitle(month)}</span>
      <button
        type="button"
        className="btn btn-ghost"
        style={{ height: 32, width: 32, padding: 0, border: "none", opacity: atLatest ? 0.35 : 1 }}
        aria-label="Next month"
        disabled={atLatest}
        onClick={() => onChange(shiftMonthKey(month, 1))}
      >
        ›
      </button>
      {month !== currentMonth ? (
        <button type="button" className="btn btn-ghost" style={{ height: 32 }} onClick={() => onChange(currentMonth)}>
          This month
        </button>
      ) : null}
    </div>
  );
}

export function ErrorBanner({ message }: { message: string }) {
  return (
    <div
      style={{
        background: "#FBF3E4",
        border: "1px solid rgba(138,100,18,.3)",
        borderRadius: 12,
        padding: "13px 16px",
        color: "#6E5A2E",
        fontSize: 13,
      }}
    >
      {message}
    </div>
  );
}
