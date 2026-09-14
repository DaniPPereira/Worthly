"use client";

import { FormEvent, useState } from "react";
import { RisingW } from "@/components/brand/RisingW";
import { PasswordInput } from "@/components/ui/PasswordInput";
import { csrfToken } from "@/lib/api";

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const response = await fetch("/api/register", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "x-csrf-token": csrfToken(),
        },
        body: JSON.stringify({ email, password }),
      });
      if (response.status === 409) {
        setError("That email is already registered.");
        return;
      }
      if (response.status === 400) {
        setError("Use a valid email and a password of at least 14 characters.");
        return;
      }
      if (!response.ok) {
        setError("Could not create the account.");
        return;
      }
      window.location.href = "/login?registered=1";
    } finally {
      setBusy(false);
    }
  }

  return (
    <main
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        background: "var(--pine-deep)",
        padding: 24,
      }}
    >
      <div style={{ width: 420, background: "var(--paper)", borderRadius: 18, padding: 32 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 40, height: 40, borderRadius: 12, background: "var(--pine)", display: "grid", placeItems: "center" }}>
            <RisingW size={22} />
          </div>
          <div style={{ font: "600 22px/1 var(--font-sans)", letterSpacing: "-.03em" }}>Worthly</div>
        </div>
        <p className="serif" style={{ fontSize: 28, margin: "22px 0 8px", lineHeight: 1.15 }}>
          Create your account.
        </p>
        <p className="muted">Each account keeps its own banks, investments and history. You sign in afterwards.</p>
        {error ? <p style={{ color: "var(--loss)", fontSize: 13 }}>{error}</p> : null}
        <form onSubmit={(event) => void onSubmit(event)} style={{ display: "grid", gap: 12, marginTop: 18 }}>
          <label className="label">
            Email
            <input
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              style={{
                display: "block",
                width: "100%",
                marginTop: 6,
                height: 44,
                padding: "0 12px",
                borderRadius: 9,
                border: "1px solid rgba(19,26,25,.12)",
              }}
            />
          </label>
          <label className="label">
            Password
            <PasswordInput
              autoComplete="new-password"
              minLength={14}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          <button type="submit" className="btn btn-primary" style={{ height: 44, width: "100%" }} disabled={busy}>
            Create account
          </button>
        </form>
        <a href="/login" className="muted" style={{ display: "block", marginTop: 16, fontSize: 13 }}>
          Already have an account? Sign in
        </a>
      </div>
    </main>
  );
}
