import type { CSSProperties } from "react";
import { LegalLayout } from "@/components/legal/LegalLayout";

export const metadata = { title: "Privacy · Worthly" };

export default function PrivacyPage() {
  return (
    <LegalLayout title="Privacy Policy">
      <p className="muted" style={{ marginTop: 0 }}>
        This policy describes how this Worthly instance handles personal and financial data. It applies
        to the website you are using. Last updated 15 September 2026.
      </p>
      <h2 style={heading}>Who operates this service</h2>
      <p>
        Worthly is a personal finance application. The operator of the server that hosts this site is
        the data controller for accounts created here. Banking and brokerage data is imported only
        after you connect a provider in the app.
      </p>
      <h2 style={heading}>What we collect</h2>
      <ul>
        <li>Account email and a password hash (Argon2id). The password itself is never stored.</li>
        <li>Reporting timezone and currency preferences.</li>
        <li>
          Bank and investment data you authorise: account metadata, balances, transactions, holdings,
          and encrypted provider session material needed to refresh that data.
        </li>
        <li>Device and session metadata used to sign you in and revoke sessions.</li>
        <li>Security audit events (for example sign-in and account deletion), without passwords or tokens.</li>
      </ul>
      <h2 style={heading}>How it is used</h2>
      <p>
        Data is used only to provide your private finance workspace: sign-in, sync, categorisation,
        analytics, exports, and operational security. It is not sold, and it is not used for advertising.
      </p>
      <h2 style={heading}>Cookies</h2>
      <p>
        The website uses an HttpOnly session cookie (<code>web_session</code>) and a CSRF cookie
        (<code>worthly_csrf</code>). The session idles after 30 minutes without use and cannot last
        more than 12 hours. OAuth tokens are not given to the browser.
      </p>
      <h2 style={heading}>Retention</h2>
      <p>
        Normalised account and transaction history stays until you delete the related connection
        (purge) or your account. Encrypted raw provider payloads are cleared automatically 30 days
        after import. Session and refresh tokens follow the published security TTLs.
      </p>
      <h2 style={heading}>Sharing</h2>
      <p>
        Bank connections use Enable Banking as a technical intermediary to your bank. Brokerage
        connections use Trading 212 with credentials you supply. Hosting, backups, and TLS
        termination may involve the operator&apos;s infrastructure providers. Those parties are not
        given Worthly as a marketing product.
      </p>
      <h2 style={heading}>Your choices</h2>
      <p>
        You can export transactions from Settings, disconnect or purge a connection, sign out or
        revoke devices, and delete your account. Account deletion removes your user record and
        associated financial data from this instance. Security audit rows are kept without your user
        id.
      </p>
      <h2 style={heading}>Security</h2>
      <p>
        Traffic is HTTPS. Provider payloads and secrets are encrypted at rest. Backups of the
        database are encrypted. Details are in the operator&apos;s security documentation for this
        deployment.
      </p>
    </LegalLayout>
  );
}

const heading: CSSProperties = {
  fontSize: 16,
  margin: "22px 0 8px",
};
