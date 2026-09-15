import { NextResponse } from "next/server";
import { apiFetch, parseFormCsrf, csrfFromCookie, readSession } from "@/lib/auth";
import { config } from "@/lib/config";
import { clearAuthCookies } from "@/lib/cookies";

function redirectAfterLogout(): NextResponse {
  const response = NextResponse.redirect(new URL("/login?signedout=1", config.origin), { status: 303 });
  clearAuthCookies(response);
  response.headers.set("Cache-Control", "no-store, no-cache, must-revalidate");
  return response;
}

async function revokeSession(): Promise<void> {
  const session = await readSession();
  if (!session) {
    return;
  }
  try {
    await apiFetch(session, "/me/logout", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    });
  } catch {
    // Token may already be invalid after an API restart.
  }
}

/** Clears a stale browser session without CSRF. Used when the API rejects the access token (401). */
export async function GET() {
  await revokeSession();
  return redirectAfterLogout();
}

export async function POST(request: Request) {
  try {
    const cookieToken = await csrfFromCookie();
    await parseFormCsrf(request, cookieToken);
  } catch {
    // Still drop local cookies; a CSRF mismatch must not leave the browser signed in.
  }
  await revokeSession();
  return redirectAfterLogout();
}
