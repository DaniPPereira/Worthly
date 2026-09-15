"use client";

import { useEffect, useMemo, useState } from "react";
import { FeatureGate } from "@/components/shell/FeatureGate";
import { CurrencyTabs } from "@/components/ui/Primitives";
import { apiGet } from "@/lib/api";
import { hasInvestmentsNav, isBank, statusTone, useAppData } from "@/lib/app-data";
import { formatAmount } from "@/lib/money";
import { formatInstant } from "@/lib/period";
import type { Account, Balance, WealthSummary } from "@/lib/types";

type AccountView = Account & { balance: Balance | null };

export function AccountsPage() {
  const { owner, privacy, connections, accounts: shellAccounts } = useAppData();
  const [accounts, setAccounts] = useState<AccountView[] | null>(null);
  const [wealth, setWealth] = useState<WealthSummary | null>(null);
  const [currency, setCurrency] = useState(owner.reportingCurrency);

  useEffect(() => {
    let cancelled = false;
    Promise.all([apiGet<Account[]>("/accounts"), apiGet<WealthSummary>("/analytics/summary")])
      .then(async ([list, nextWealth]) => {
        const withBalances = await Promise.all(
          list.map(async (account) => {
            try {
              const balances = await apiGet<Balance[]>(`/accounts/${account.id}/balances`);
              const selected = balances.find((item) => item.usedForLiquidCash) ?? balances[0] ?? null;
              return { ...account, balance: selected };
            } catch {
              return { ...account, balance: null };
            }
          }),
        );
        if (!cancelled) {
          setAccounts(withBalances);
          setWealth(nextWealth);
          const first = nextWealth.totalsByCurrency[0]?.currency ?? owner.reportingCurrency;
          setCurrency((current) => (nextWealth.totalsByCurrency.some((row) => row.currency === current) ? current : first));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setAccounts([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [owner.reportingCurrency]);

  const visible = useMemo(() => (accounts ?? []).filter((account) => account.currency === currency), [accounts, currency]);
  const wealthRow = wealth?.totalsByCurrency.find((row) => row.currency === currency);
  const currencies = wealth?.totalsByCurrency.map((row) => row.currency) ?? [];
  const bankNeedsReview = connections.some((connection) => isBank(connection) && connection.status !== "ACTIVE");
  const tone = statusTone(bankNeedsReview ? "REAUTH_REQUIRED" : "ACTIVE");

  if (!accounts) {
    return <p className="muted">Loading accounts…</p>;
  }

  return (
    <FeatureGate allowed={shellAccounts.length > 0}>
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <CurrencyTabs currencies={currencies} selected={currency} onSelect={setCurrency} />
      <div style={{ display: "grid", gridTemplateColumns: hasInvestmentsNav(connections) ? "repeat(3,1fr)" : "repeat(2,1fr)", gap: 16 }}>
        <SummaryCard label="Total liquid cash" value={wealthRow ? formatAmount(wealthRow.liquidCash, currency, privacy) : "—"} />
        {hasInvestmentsNav(connections) ? (
          <SummaryCard label="Investments" value={wealthRow ? formatAmount(wealthRow.investmentValue, currency, privacy) : "—"} />
        ) : null}
        <SummaryCard label="Net worth" value={wealthRow ? formatAmount(wealthRow.netWorth, currency, privacy) : "—"} />
      </div>
      <div className="card" style={{ overflow: "hidden" }}>
        <div style={{ padding: "16px 20px", display: "flex", alignItems: "center", gap: 11, borderBottom: "1px solid rgba(19,26,25,.07)" }}>
          <div style={{ width: 28, height: 28, borderRadius: 8, background: "rgba(19,26,25,.08)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 600, fontSize: 12 }}>
            B
          </div>
          <div style={{ flex: 1 }}>
            <span style={{ fontWeight: 600, fontSize: 14 }}>Accounts</span>
            <span style={{ fontSize: 11.5, color: "var(--faint)", marginLeft: 10 }}>{currency}</span>
          </div>
          <span className="mono" style={{ fontSize: 9.5, letterSpacing: ".08em", textTransform: "uppercase", color: tone.fg, border: `1px solid ${tone.bd}`, borderRadius: 4, padding: "3px 6px" }}>
            {tone.label}
          </span>
        </div>
        {visible.length === 0 ? null : (
          visible.map((account) => (
            <div
              key={account.id}
              style={{
                display: "grid",
                gridTemplateColumns: "1.3fr 190px 130px 160px",
                gap: 14,
                padding: "13px 20px",
                borderBottom: "1px solid rgba(19,26,25,.05)",
                alignItems: "center",
              }}
            >
              <span style={{ fontWeight: 500, fontSize: 13 }}>{account.displayName}</span>
              <span className="mono" style={{ fontSize: 12, color: "var(--faint)" }}>
                {account.maskedIdentifier ?? "—"}
              </span>
              <span style={{ fontSize: 11.5, color: "var(--faint)" }}>{accountType(account.type)}</span>
              <span style={{ textAlign: "right" }}>
                <span className="mono tabular" style={{ fontSize: 14, fontWeight: 500 }}>
                  {account.balance ? formatAmount(account.balance.money.amount, account.balance.money.currency, privacy) : "—"}
                </span>
                <span style={{ display: "block", fontSize: 10.5, color: "var(--faint)", marginTop: 2 }}>
                  {account.balance ? `Updated ${formatInstant(account.balance.observedAt, owner.reportingTimezone)}` : "No snapshot"}
                </span>
              </span>
            </div>
          ))
        )}
      </div>
    </div>
    </FeatureGate>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="card" style={{ padding: 18, borderRadius: 14 }}>
      <div className="label">{label}</div>
      <div className="serif tabular" style={{ fontSize: 32, lineHeight: 1.1, marginTop: 7 }}>
        {value}
      </div>
    </div>
  );
}

function accountType(type: string): string {
  switch (type) {
    case "CURRENT":
      return "Current account";
    case "SAVINGS":
      return "Savings";
    case "CARD":
      return "Card";
    case "BROKERAGE":
      return "Brokerage";
    default:
      return type;
  }
}
