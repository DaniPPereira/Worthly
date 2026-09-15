"use client";

import { useEffect } from "react";

export function EndOauthSession({ href }: { href: string }) {
  useEffect(() => {
    window.location.replace(href);
  }, [href]);
  return <p className="muted">Signing out…</p>;
}
