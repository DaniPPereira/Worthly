"use client";

import { useAppData } from "@/lib/app-data";

const TITLES: Record<string, string> = {
  "/": "Dashboard",
  "/transactions": "Transactions",
  "/investments": "Investments",
  "/accounts": "Accounts",
  "/connections": "Connections",
  "/settings": "Settings",
};

export function TopBar({ pathname }: { pathname: string }) {
  const { privacy, setPrivacy, syncing, stamp, syncAll } = useAppData();
  const title = TITLES[pathname] ?? TITLES[Object.keys(TITLES).find((key) => pathname.startsWith(key) && key !== "/") ?? ""] ?? "Worthly";

  return (
    <header
      style={{
        flex: "none",
        padding: "20px 30px 16px",
        borderBottom: "1px solid rgba(19,26,25,.09)",
        display: "flex",
        alignItems: "center",
        gap: 14,
        background: "var(--paper)",
      }}
    >
      <div>
        <div style={{ font: "600 22px/1.1 var(--font-sans)", color: "var(--ink)", letterSpacing: "-.02em" }}>{title}</div>
        <div style={{ display: "flex", alignItems: "center", gap: 7, marginTop: 5 }}>
          <div style={{ width: 6, height: 6, borderRadius: "50%", background: syncing ? "var(--brass)" : "var(--gain)" }} />
          <span style={{ fontSize: 11.5, color: "var(--faint)" }}>{stamp}</span>
        </div>
      </div>
      <div style={{ flex: 1 }} />
      <button
        type="button"
        onClick={() => setPrivacy(!privacy)}
        style={{
          height: 36,
          padding: "0 12px",
          borderRadius: 9,
          border: `1px solid ${privacy ? "var(--pine)" : "rgba(19,26,25,.12)"}`,
          background: privacy ? "var(--pine)" : "var(--white)",
          color: privacy ? "var(--cream)" : "#3E4A47",
          display: "flex",
          alignItems: "center",
          gap: 8,
          font: "500 12.5px var(--font-sans)",
        }}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round">
          <path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6-10-6-10-6z" />
          <circle cx="12" cy="12" r="2.4" />
        </svg>
        {privacy ? "Values hidden" : "Hide values"}
      </button>
      <button type="button" className="btn btn-primary" onClick={() => void syncAll()} disabled={syncing}>
        <span style={{ display: "flex", animation: syncing ? "wspin .9s linear infinite" : undefined }}>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#F4F1EA" strokeWidth="1.9" strokeLinecap="round">
            <path d="M20 12a8 8 0 1 1-2.3-5.6" />
            <path d="M20 4v4h-4" />
          </svg>
        </span>
        {syncing ? "Syncing…" : "Sync now"}
      </button>
    </header>
  );
}
