"use client";

import { useEffect, useState } from "react";

type Owner = {
  email: string;
  reportingTimezone: string;
  reportingCurrency: string;
};

function csrfToken(): string {
  const match = document.cookie.split("; ").find((part) => part.startsWith("worthly_csrf="));
  return match ? decodeURIComponent(match.split("=")[1] ?? "") : "";
}

export default function HomePage() {
  const [owner, setOwner] = useState<Owner | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetch("/api/worthly/me", { credentials: "same-origin" })
      .then(async (response) => {
        if (response.status === 401) {
          return null;
        }
        if (!response.ok) {
          throw new Error("profile_unavailable");
        }
        return (await response.json()) as Owner;
      })
      .then((value) => {
        if (!cancelled) {
          setOwner(value);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError("Unable to load your profile.");
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <main>
        <h1>Worthly</h1>
        <p>Loading…</p>
      </main>
    );
  }

  if (!owner) {
    return (
      <main>
        <h1>Worthly</h1>
        <p>Sign in to manage your household finances.</p>
        {error ? <p>{error}</p> : null}
        <p>
          <a href="/login">Sign in</a>
        </p>
      </main>
    );
  }

  return (
    <main>
      <h1>Worthly</h1>
      <p>Signed in as {owner.email}</p>
      <p>
        Reporting timezone {owner.reportingTimezone}, currency {owner.reportingCurrency}.
      </p>
      <form method="post" action="/logout">
        <input type="hidden" name="csrf" value={csrfToken()} />
        <button type="submit">Sign out</button>
      </form>
    </main>
  );
}
