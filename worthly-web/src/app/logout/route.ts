import { NextResponse } from "next/server";
import { apiFetch, parseFormCsrf, csrfFromCookie, readSession } from "@/lib/auth";
import { config } from "@/lib/config";
import { CSRF_COOKIE, SESSION_COOKIE } from "@/lib/session";

export async function POST(request: Request) {
  const cookieToken = await csrfFromCookie();
  await parseFormCsrf(request, cookieToken);
  const session = await readSession();
  if (session) {
    await apiFetch(session, "/me/logout", { method: "POST" });
  }
  const response = NextResponse.redirect(new URL("/login", config.origin), { status: 303 });
  response.cookies.delete(SESSION_COOKIE);
  response.cookies.delete(CSRF_COOKIE);
  return response;
}
