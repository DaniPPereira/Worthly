import { NextResponse } from "next/server";
import { exchangeCode, readOauthFlow } from "@/lib/auth";
import { config } from "@/lib/config";
import { OAUTH_FLOW_COOKIE, SESSION_COOKIE, sessionCookieOptions } from "@/lib/session";
import { encryptPayload } from "@/lib/session";

export async function GET(request: Request) {
  const url = new URL(request.url);
  const error = url.searchParams.get("error");
  if (error) {
    return NextResponse.redirect(new URL("/login?error=auth", config.origin));
  }
  const code = url.searchParams.get("code");
  const state = url.searchParams.get("state");
  const flow = await readOauthFlow();
  if (!code || !state || !flow || flow.state !== state) {
    return NextResponse.redirect(new URL("/login?error=state", config.origin));
  }
  const session = await exchangeCode(code, flow.verifier);
  const response = NextResponse.redirect(new URL("/", config.origin));
  response.cookies.set(SESSION_COOKIE, await encryptPayload(session), sessionCookieOptions);
  response.cookies.delete(OAUTH_FLOW_COOKIE);
  return response;
}
