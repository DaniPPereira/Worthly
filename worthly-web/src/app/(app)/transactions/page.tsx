"use client";

import { Suspense } from "react";
import { TransactionsPage } from "@/components/screens/TransactionsPage";

export default function Page() {
  return (
    <Suspense fallback={<p className="muted">Loading transactions…</p>}>
      <TransactionsPage />
    </Suspense>
  );
}
