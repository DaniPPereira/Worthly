import { BrandMark, PublicPanel } from "@/components/brand/PublicSplit";
import { EndOauthSession } from "@/components/brand/EndOauthSession";
import { config } from "@/lib/config";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string; registered?: string; signedout?: string }>;
}) {
  const params = await searchParams;
  if (params.signedout === "1") {
    return (
      <PublicPanel panelKey="land">
        <div className="public-brand">
          <BrandMark />
          <div className="public-brand__name">Worthly</div>
        </div>
        <p className="serif public-panel__title">Start here.</p>
        <EndOauthSession href={`${config.issuer}/logout`} />
      </PublicPanel>
    );
  }
  const error =
    params.error === "auth"
      ? "Sign-in was cancelled or failed."
      : params.error === "state"
        ? "The sign-in attempt expired. Please try again."
        : params.error === "token"
          ? "Sign-in could not be completed. Please try again."
          : null;
  const registered = params.registered === "1";

  return (
    <PublicPanel panelKey="land">
      <div className="public-brand">
        <BrandMark />
        <div className="public-brand__name">Worthly</div>
      </div>
      <p className="serif public-panel__title">Start here.</p>
      <p className="muted">Create an account, or sign in.</p>
      {registered ? <p className="public-panel__ok">Account created. Sign in to continue.</p> : null}
      {error ? <p className="public-panel__err">{error}</p> : null}
      <a href="/register" className="btn btn-primary public-panel__cta">
        Create an account
      </a>
      <a href="/login/start" className="btn btn-ghost public-panel__cta">
        Sign in
      </a>
      <p className="muted public-panel__legal">
        <a href="/privacy">Privacy</a>
        {" · "}
        <a href="/terms">Terms</a>
      </p>
    </PublicPanel>
  );
}
