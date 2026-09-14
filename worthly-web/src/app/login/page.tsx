import { RisingW } from "@/components/brand/RisingW";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string; registered?: string }>;
}) {
  const params = await searchParams;
  const error =
    params.error === "auth"
      ? "Sign-in was cancelled or failed."
      : params.error === "state"
        ? "The sign-in attempt expired. Please try again."
        : null;
  const registered = params.registered === "1";

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
          Private finance, on your server.
        </p>
        <p className="muted">
          Sign in to continue. The next screen is your Worthly login — the browser never receives OAuth tokens.
        </p>
        {registered ? <p style={{ color: "var(--gain)", fontSize: 13 }}>Account created. Sign in to continue.</p> : null}
        {error ? <p style={{ color: "var(--loss)", fontSize: 13 }}>{error}</p> : null}
        <a href="/login/start" className="btn btn-primary" style={{ marginTop: 18, textDecoration: "none", height: 44, width: "100%" }}>
          Sign in
        </a>
        <a href="/register" className="muted" style={{ display: "block", marginTop: 16, fontSize: 13, textAlign: "center" }}>
          Create an account
        </a>
      </div>
    </main>
  );
}
