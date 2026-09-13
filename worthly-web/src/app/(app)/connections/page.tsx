"use client";

import { Suspense } from "react";
import { ConnectionsPage } from "@/components/screens/ConnectionsPage";

export default function Page() {
  return (
    <Suspense fallback={<p className="muted">Loading connections…</p>}>
      <ConnectionsPage />
    </Suspense>
  );
}
