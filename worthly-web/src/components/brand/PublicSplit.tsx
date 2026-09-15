import type { ReactNode } from "react";
import { RisingW } from "@/components/brand/RisingW";

export function BrandMark({ onDark = false, size = 22 }: { onDark?: boolean; size?: number }) {
  return (
    <div
      className="public-mark"
      style={{ background: onDark ? "rgba(244,241,234,.1)" : "var(--pine)" }}
    >
      <RisingW size={size} onDark />
    </div>
  );
}

export function PublicSplit({ children }: { children: ReactNode }) {
  return (
    <div className="public-split">
      <section className="public-split__left">
        <div className="label" style={{ color: "var(--gold)" }}>
          Worthly
        </div>
        <h1 className="serif public-split__headline">See what you have.</h1>
        <p className="public-split__lede">Banks, cash and investments.</p>
        <div className="public-split__promise">
          <div className="label" style={{ color: "rgba(244,241,234,.55)" }}>
            Your account
          </div>
          <p>Private. Read-only.</p>
        </div>
      </section>
      <section className="public-split__right">{children}</section>
    </div>
  );
}

export { PublicPanel } from "./PublicPanel";
