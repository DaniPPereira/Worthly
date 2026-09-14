import { NextResponse } from "next/server";
import { apiFetch, parseFormCsrf, csrfFromCookie, readSession } from "@/lib/auth";
import { config } from "@/lib/config";
import {
  CSRF_COOKIE,
  OAUTH_FLOW_COOKIE,
  SESSION_COOKIE,
  oauthFlowCookieOptions,
  sessionCookieOptions,
} from "@/lib/session";

function redirectToLogin(): NextResponse {
  const response = NextResponse.redirect(new URL("/login", config.origin), { status: 303 });
  response.cookies.set(SESSION_COOKIE, "", { ...sessionCookieOptions, maxAge: 0 });
  response.cookies.set(OAUTH_FLOW_COOKIE, "", { ...oauthFlowCookieOptions, maxAge: 0 });
  return response;
}

/** Clears a stale browser session without CSRF. Used when the API rejects the access token (401). */
export async function GET() {
  return redirectToLogin();
}

export async function POST(request: Request) {
  const cookieToken = await csrfFromCookie();
  await parseFormCsrf(request, cookieToken);
  const session = await readSession();
  if (session) {
    try {
      await apiFetch(session, "/me/logout", { method: "POST" });
    } catch {
      // Token may already be invalid after an API restart.
    }
  }
  const response = redirectToLogin();
  response.cookies.set(CSRF_COOKIE, "", { path: "/", maxAge: 0 });
  return response;
}
