"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { RisingW } from "@/components/brand/RisingW";
import {
  connectionLabel,
  ownerInitial,
  ownerLabel,
  statusTone,
  useAppData,
} from "@/lib/app-data";

const NAV = [
  { href: "/", label: "Dashboard", match: (path: string) => path === "/" },
  { href: "/transactions", label: "Transactions", match: (path: string) => path.startsWith("/transactions") },
  { href: "/investments", label: "Investments", match: (path: string) => path.startsWith("/investments") },
  { href: "/accounts", label: "Accounts", match: (path: string) => path.startsWith("/accounts") },
  { href: "/connections", label: "Connections", match: (path: string) => path.startsWith("/connections") },
  { href: "/settings", label: "Settings", match: (path: string) => path.startsWith("/settings") },
] as const;

function NavIcon({ label }: { label: string }) {
  const common = {
    width: 17,
    height: 17,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.8,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
  };
  switch (label) {
    case "Dashboard":
      return (
        <svg {...common}>
          <rect x="3" y="3" width="7.5" height="8.5" rx="1.6" />
          <rect x="13.5" y="3" width="7.5" height="5" rx="1.6" />
          <rect x="3" y="14.5" width="7.5" height="6.5" rx="1.6" />
          <rect x="13.5" y="11" width="7.5" height="10" rx="1.6" />
        </svg>
      );
    case "Transactions":
      return (
        <svg {...common}>
          <path d="M4 8h13l-3-3M20 16H7l3 3" />
        </svg>
      );
    case "Investments":
      return (
        <svg {...common}>
          <path d="M4 19V5M4 19h16M8 15l3.5-4.5 3 2.5L20 7" />
        </svg>
      );
    case "Accounts":
      return (
        <svg {...common}>
          <rect x="3" y="6" width="18" height="12" rx="2.5" />
          <path d="M3 10.5h18" />
        </svg>
      );
    case "Connections":
      return (
        <svg {...common}>
          <path d="M9 15 4.5 19.5M15 9 19.5 4.5" />
          <rect x="7" y="10" width="7" height="7" rx="3.5" transform="rotate(-45 7 10)" />
        </svg>
      );
    default:
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="3" />
          <path d="M12 3v2.5M12 18.5V21M4.2 7.5l2.2 1.3M17.6 15.2l2.2 1.3M4.2 16.5l2.2-1.3M17.6 8.8l2.2-1.3" />
        </svg>
      );
  }
}

export function Sidebar() {
  const pathname = usePathname();
  const { owner, connections } = useAppData();
  const reauthCount = connections.filter(
    (item) => item.status === "REAUTH_REQUIRED" || item.status === "CONFIGURATION_REQUIRED",
  ).length;

  return (
    <aside
      style={{
        width: "var(--sidebar)",
        flex: "none",
        alignSelf: "stretch",
        height: "100%",
        minHeight: 0,
        overflow: "auto",
        background: "var(--pine-deep)",
        display: "flex",
        flexDirection: "column",
        padding: "22px 14px 16px",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 11, padding: "0 8px 22px" }}>
        <RisingW />
        <div style={{ font: "600 19px/1 var(--font-sans)", color: "var(--cream)", letterSpacing: "-.03em" }}>Worthly</div>
      </div>
      <nav>
        {NAV.map((item) => {
          const active = item.match(pathname);
          const badge = item.label === "Connections" && reauthCount > 0 ? String(reauthCount) : null;
          return (
            <Link
              key={item.href}
              href={item.href}
              style={{
                width: "100%",
                textAlign: "left",
                textDecoration: "none",
                background: active ? "rgba(244,241,234,.1)" : "transparent",
                color: active ? "var(--cream)" : "rgba(244,241,234,.66)",
                borderRadius: 9,
                padding: "10px 11px",
                marginBottom: 2,
                display: "flex",
                alignItems: "center",
                gap: 11,
                font: "500 13.5px var(--font-sans)",
              }}
            >
              <NavIcon label={item.label} />
              <span style={{ flex: 1 }}>{item.label}</span>
              {badge ? (
                <span
                  className="mono"
                  style={{
                    fontSize: 9.5,
                    fontWeight: 600,
                    background: "var(--brass)",
                    color: "#2A1E05",
                    borderRadius: 4,
                    padding: "2px 5px",
                  }}
                >
                  {badge}
                </span>
              ) : null}
            </Link>
          );
        })}
      </nav>
      <div style={{ marginTop: "auto", padding: "14px 11px 10px", borderTop: "1px solid rgba(244,241,234,.13)" }}>
        <div
          className="mono"
          style={{
            fontSize: 9.5,
            fontWeight: 500,
            letterSpacing: ".14em",
            textTransform: "uppercase",
            color: "rgba(244,241,234,.66)",
          }}
        >
          Providers
        </div>
        {connections.length === 0 ? (
          <div style={{ marginTop: 9, fontSize: 12, color: "rgba(244,241,234,.66)" }}>None connected</div>
        ) : (
          connections.map((connection) => {
            const tone = statusTone(connection.status);
            return (
              <div key={connection.id} style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 9 }}>
                <div style={{ width: 6, height: 6, borderRadius: "50%", background: tone.dot, flex: "none" }} />
                <span style={{ flex: 1, fontSize: 12, color: "rgba(244,241,234,.82)" }}>{connectionLabel(connection)}</span>
                <span className="mono" style={{ fontSize: 10, fontWeight: 500, color: tone.dot === "#14654A" ? "#8FD0B4" : tone.dot === "#C98F32" ? "#E8C382" : "#F4F1EA" }}>
                  {tone.label === "Active" ? "Active" : tone.label === "Reauth required" ? "Reauth" : tone.label.split(" ")[0]}
                </span>
              </div>
            );
          })
        )}
      </div>
      <div
        style={{
          padding: "12px 11px 0",
          display: "flex",
          alignItems: "center",
          gap: 9,
          borderTop: "1px solid rgba(244,241,234,.13)",
          marginTop: 12,
        }}
      >
        <div
          style={{
            width: 26,
            height: 26,
            borderRadius: "50%",
            background: "var(--pine)",
            border: "1px solid rgba(244,241,234,.2)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            font: "600 11px var(--font-sans)",
            color: "var(--cream)",
          }}
        >
          {ownerInitial(owner)}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ font: "500 12px var(--font-sans)", color: "var(--cream)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
            {ownerLabel(owner)}
          </div>
        </div>
      </div>
    </aside>
  );
}
