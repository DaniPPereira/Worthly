"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

export function FeatureGate({ allowed, children }: { allowed: boolean; children: ReactNode }) {
  const router = useRouter();
  useEffect(() => {
    if (!allowed) {
      router.replace("/");
    }
  }, [allowed, router]);
  if (!allowed) {
    return null;
  }
  return children;
}
