import { BrandMark, PublicPanel } from "@/components/brand/PublicSplit";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string; registered?: string; enter?: string }>;
}) {
  const params = await searchParams;
  const error =
    params.error === "auth"
      ? "Sign-in was cancelled or failed."
      : params.error === "state"
        ? "The sign-in attempt expired. Please try again."
        : params.error === "token"
          ? "Sign-in could not be completed. Please try again."
          : null;
  const registered = params.registered === "1";
  const enter = params.enter === "1" || registered || Boolean(error);

  if (!enter) {
    return (
      <PublicPanel key="land" panelKey="land">
        <div className="public-brand">
          <BrandMark />
          <div className="public-brand__name">Worthly</div>
        </div>
        <p className="serif public-panel__title">Start here.</p>
        <p className="muted">Create an account, or sign in.</p>
        <a href="/register" className="btn btn-primary public-panel__cta">
          Create an account
        </a>
        <a href="/login?enter=1" className="btn btn-ghost public-panel__cta">
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

  return (
    <PublicPanel key="enter" panelKey="enter">
      <div className="public-brand">
        <BrandMark />
        <div className="public-brand__name">Worthly</div>
      </div>
      <p className="serif public-panel__title">Welcome back.</p>
      <p className="muted">Sign in to continue.</p>
      {registered ? <p className="public-panel__ok">Account created. Sign in to continue.</p> : null}
      {error ? <p className="public-panel__err">{error}</p> : null}
      <a href="/login/start" className="btn btn-primary public-panel__cta">
        Sign in
      </a>
      <a href="/register" className="muted public-panel__link">
        Create an account
      </a>
      <a href="/login" className="muted public-panel__back">
        Back
      </a>
      <p className="muted public-panel__legal">
        <a href="/privacy">Privacy</a>
        {" · "}
        <a href="/terms">Terms</a>
      </p>
    </PublicPanel>
  );
}
