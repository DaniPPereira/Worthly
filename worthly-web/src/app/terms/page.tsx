import type { CSSProperties } from "react";
import { LegalLayout } from "@/components/legal/LegalLayout";

export const metadata = { title: "Terms of Use · Worthly" };

export default function TermsPage() {
  return (
    <LegalLayout title="Terms of Use">
      <p className="muted" style={{ marginTop: 0 }}>
        These terms govern use of this Worthly instance. Last updated 15 September 2026.
      </p>
      <h2 style={heading}>The service</h2>
      <p>
        Worthly helps you view accounts, transactions and investments that you connect. It is
        read-only toward banks and brokers: it does not initiate payments or trades. Anyone can
        create an account with email and password.
      </p>
      <h2 style={heading}>Your account</h2>
      <p>
        You must provide a valid email and a password of at least 8 characters. You are responsible
        for keeping that password confidential. Do not share your login. The
        operator may lock or disable an account that is abused or that threatens the security of the
        server.
      </p>
      <h2 style={heading}>Connected institutions</h2>
      <p>
        When you connect a bank or Trading 212, you instruct Worthly to retrieve data on your behalf
        under that provider&apos;s terms. Consent windows expire; you may need to reconnect. Worthly
        is not your bank, broker, or financial adviser, and figures in the app can lag or differ from
        the institution.
      </p>
      <h2 style={heading}>Acceptable use</h2>
      <p>
        Use the service only for your own finances (or those you are authorised to manage). Do not
        attempt to access other people&apos;s data, overload the API, bypass authentication, or use
        the service to commit fraud.
      </p>
      <h2 style={heading}>Availability and liability</h2>
      <p>
        The operator provides this instance without a guaranteed uptime SLA. Data can be lost to
        provider errors, bugs, or a failed restore. To the extent permitted by law, the operator is
        not liable for lost profits, missed payments, or investment decisions made from Worthly
        figures. Keep independent records for anything that matters legally or fiscally.
      </p>
      <h2 style={heading}>Termination</h2>
      <p>
        You may delete your account at any time from Settings. The operator may stop offering this
        instance. After deletion, financial data for that account is removed as described in the
        Privacy Policy.
      </p>
      <h2 style={heading}>Changes</h2>
      <p>
        Material changes to these terms or the Privacy Policy will be reflected on these pages. Continued
        use after an update means you accept the revised text.
      </p>
    </LegalLayout>
  );
}

const heading: CSSProperties = {
  fontSize: 16,
  margin: "22px 0 8px",
};
