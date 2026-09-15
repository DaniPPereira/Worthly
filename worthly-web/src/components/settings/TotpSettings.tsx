"use client";

import { useEffect, useState } from "react";
import { apiGet, apiSend } from "@/lib/api";

type TotpStatus = { enabled: boolean };
type TotpSetup = { secret: string; otpauthUri: string; qrSvg: string };
type TotpConfirm = { recoveryCodes: string[] };

export function TotpSettings() {
  const [enabled, setEnabled] = useState(false);
  const [setup, setSetup] = useState<TotpSetup | null>(null);
  const [code, setCode] = useState("");
  const [recovery, setRecovery] = useState<string[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    try {
      const status = await apiGet<TotpStatus>("/me/totp");
      setEnabled(status.enabled);
    } catch {
      setEnabled(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function start() {
    setError(null);
    setBusy(true);
    try {
      const next = await apiSend<TotpSetup>("POST", "/me/totp/start");
      setSetup(next);
      setRecovery(null);
      setCode("");
    } catch {
      setError("Could not start authenticator setup.");
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    setError(null);
    setBusy(true);
    try {
      const next = await apiSend<TotpConfirm>("POST", "/me/totp/confirm", { code: code.trim() });
      setRecovery(next?.recoveryCodes ?? []);
      setSetup(null);
      setCode("");
      setEnabled(true);
    } catch {
      setError("That code was not accepted. Wait for a new one and try again.");
    } finally {
      setBusy(false);
    }
  }

  async function disable() {
    setError(null);
    setBusy(true);
    try {
      await apiSend("POST", "/me/totp/disable", { code: code.trim() });
      setEnabled(false);
      setSetup(null);
      setRecovery(null);
      setCode("");
    } catch {
      setError("Enter a current authenticator or recovery code to turn this off.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ padding: "14px 20px", borderBottom: "1px solid rgba(19,26,25,.05)" }}>
      <div style={{ fontWeight: 500, fontSize: 13.5 }}>Authenticator app</div>
      <div style={{ fontSize: 11.5, color: "var(--faint)", marginTop: 2, lineHeight: 1.45 }}>
        Optional. Bitwarden, 1Password, Aegis, 2FAS, or Google Authenticator all work. Off by default.
      </div>
      {error ? <p style={{ color: "var(--loss)", fontSize: 12, margin: "8px 0 0" }}>{error}</p> : null}

      {!enabled && !setup ? (
        <button type="button" className="btn btn-primary" style={{ height: 32, marginTop: 12 }} disabled={busy} onClick={() => void start()}>
          Turn on
        </button>
      ) : null}

      {setup ? (
        <div style={{ marginTop: 14 }}>
          <div
            style={{ width: 168, background: "var(--paper)", borderRadius: 12, padding: 8 }}
            dangerouslySetInnerHTML={{ __html: setup.qrSvg }}
          />
          <p className="muted" style={{ margin: "10px 0 6px", fontSize: 12 }}>
            Scan the QR, or enter this key manually:
          </p>
          <code className="mono" style={{ fontSize: 12.5, wordBreak: "break-all" }}>
            {setup.secret}
          </code>
          <input
            value={code}
            onChange={(event) => setCode(event.target.value)}
            placeholder="6-digit code"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={32}
            style={fieldStyle}
          />
          <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
            <button type="button" className="btn btn-ghost" style={{ height: 32 }} disabled={busy} onClick={() => setSetup(null)}>
              Cancel
            </button>
            <button type="button" className="btn btn-primary" style={{ height: 32 }} disabled={busy || code.trim().length < 6} onClick={() => void confirm()}>
              Confirm
            </button>
          </div>
        </div>
      ) : null}

      {recovery ? (
        <div style={{ marginTop: 14 }}>
          <p style={{ margin: 0, fontSize: 13, fontWeight: 500 }}>Save these recovery codes</p>
          <p className="muted" style={{ margin: "4px 0 8px", fontSize: 12 }}>
            Shown once. Each code signs in if you lose the app.
          </p>
          <ul className="mono" style={{ margin: 0, padding: "10px 12px 10px 28px", background: "#FBF9F5", borderRadius: 10, fontSize: 13 }}>
            {recovery.map((item) => (
              <li key={item} style={{ margin: "3px 0" }}>
                {item}
              </li>
            ))}
          </ul>
          <button type="button" className="btn btn-ghost" style={{ height: 32, marginTop: 10 }} onClick={() => setRecovery(null)}>
            I saved them
          </button>
        </div>
      ) : null}

      {enabled && !recovery ? (
        <div style={{ marginTop: 12 }}>
          <div className="muted" style={{ fontSize: 12, marginBottom: 8 }}>
            On. Sign-in will ask for a code after your password.
          </div>
          <input
            value={code}
            onChange={(event) => setCode(event.target.value)}
            placeholder="Code to turn off"
            autoComplete="one-time-code"
            maxLength={32}
            style={fieldStyle}
          />
          <button type="button" className="btn btn-danger" style={{ height: 32, marginTop: 10 }} disabled={busy || code.trim().length < 6} onClick={() => void disable()}>
            Turn off
          </button>
        </div>
      ) : null}
    </div>
  );
}

const fieldStyle = {
  display: "block" as const,
  width: "100%",
  marginTop: 10,
  height: 36,
  padding: "0 10px",
  borderRadius: 8,
  border: "1px solid rgba(19,26,25,.12)",
  font: "500 13px var(--font-sans)",
};
