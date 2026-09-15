"use client";

import type { ReactNode } from "react";

export function PublicPanel({ children, panelKey }: { children: ReactNode; panelKey: string }) {
  return (
    <div key={panelKey} className="public-panel">
      {children}
    </div>
  );
}
