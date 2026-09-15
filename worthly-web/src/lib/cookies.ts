import { NextResponse } from "next/server";
import {
  CSRF_COOKIE,
  OAUTH_FLOW_COOKIE,
  SESSION_COOKIE,
  csrfCookieOptions,
  oauthFlowCookieOptions,
  sessionCookieOptions,
} from "./session";

type CookieOptions = {
  httpOnly: boolean;
  secure: boolean;
  sameSite: "lax";
  path: string;
};

export function expireCookie(response: NextResponse, name: string, options: CookieOptions): void {
  response.cookies.set(name, "", {
    ...options,
    expires: new Date(0),
    maxAge: 0,
  });
}

export function clearAuthCookies(response: NextResponse): NextResponse {
  expireCookie(response, SESSION_COOKIE, sessionCookieOptions);
  expireCookie(response, OAUTH_FLOW_COOKIE, oauthFlowCookieOptions);
  expireCookie(response, CSRF_COOKIE, csrfCookieOptions);
  return response;
}
