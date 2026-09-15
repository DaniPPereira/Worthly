import type { ReactNode } from "react";
import { RisingW } from "@/components/brand/RisingW";

export function LegalLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main
      style={{
        minHeight: "100vh",
        background: "var(--pine-deep)",
        padding: "32px 24px 48px",
      }}
    >
      <article
        style={{
          width: "min(720px, 100%)",
          margin: "0 auto",
          background: "var(--paper)",
          borderRadius: 18,
          padding: "32px 36px 40px",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: 12,
              background: "var(--pine)",
              display: "grid",
              placeItems: "center",
            }}
          >
            <RisingW size={22} />
          </div>
          <div style={{ font: "600 22px/1 var(--font-sans)", letterSpacing: "-.03em" }}>Worthly</div>
        </div>
        <h1 className="serif" style={{ fontSize: 32, margin: "22px 0 8px", lineHeight: 1.15, fontWeight: 400 }}>
          {title}
        </h1>
        <div className="legal-copy" style={{ fontSize: 15, lineHeight: 1.65, color: "var(--ink)" }}>
          {children}
        </div>
        <p className="muted" style={{ marginTop: 28, fontSize: 13 }}>
          <a href="/login">Sign in</a>
          {" · "}
          <a href="/privacy">Privacy</a>
          {" · "}
          <a href="/terms">Terms</a>
        </p>
      </article>
    </main>
  );
}
