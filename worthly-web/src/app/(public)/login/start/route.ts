import { NextResponse } from "next/server";
import { authorizeUrl, encryptFlow } from "@/lib/auth";
import { config, requireSecrets } from "@/lib/config";
import { pkceChallenge, pkceVerifier, randomCsrf, randomState } from "@/lib/pkce";
import { CSRF_COOKIE, OAUTH_FLOW_COOKIE, csrfCookieOptions, oauthFlowCookieOptions } from "@/lib/session";

export async function GET(request: Request) {
  requireSecrets();
  const verifier = pkceVerifier();
  const state = randomState();
  const url = authorizeUrl({
    response_type: "code",
    client_id: config.clientId,
    redirect_uri: `${config.origin}/auth/callback`,
    scope: "openid profile worthly.read worthly.write",
    code_challenge: pkceChallenge(verifier),
    code_challenge_method: "S256",
    prompt: "login",
    state,
  });
  const response = NextResponse.redirect(url);
  response.cookies.set(OAUTH_FLOW_COOKIE, await encryptFlow({ state, verifier }), oauthFlowCookieOptions);
  if (!request.headers.get("cookie")?.includes(`${CSRF_COOKIE}=`)) {
    response.cookies.set(CSRF_COOKIE, randomCsrf(), csrfCookieOptions);
  }
  return response;
}
