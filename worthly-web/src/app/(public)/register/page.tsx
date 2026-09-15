"use client";

import { FormEvent, useState } from "react";
import { BrandMark, PublicPanel } from "@/components/brand/PublicSplit";
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
        setError("Use a valid email and a password of at least 8 characters.");
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
    <PublicPanel panelKey="register">
      <div className="public-brand">
        <BrandMark />
        <div className="public-brand__name">Worthly</div>
      </div>
      <p className="serif public-panel__title">Create your account.</p>
      <p className="muted">Then sign in.</p>
      {error ? <p className="public-panel__err">{error}</p> : null}
      <form onSubmit={(event) => void onSubmit(event)} className="public-form">
        <label className="label">
          Email
          <input
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="public-input"
          />
        </label>
        <label className="label">
          Password
          <PasswordInput
            autoComplete="new-password"
            minLength={8}
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <button type="submit" className="btn btn-primary public-panel__cta" disabled={busy}>
          Create account
        </button>
      </form>
      <a href="/login?enter=1" className="muted public-panel__link">
        Already have an account? Sign in
      </a>
      <p className="muted public-panel__legal">
        <a href="/privacy">Privacy</a>
        {" · "}
        <a href="/terms">Terms</a>
      </p>
    </PublicPanel>
  );
}
