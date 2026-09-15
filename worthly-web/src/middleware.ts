import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { contentSecurityPolicy } from "@/lib/csp";

function csrfToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

function nonce(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

const PUBLIC_PREFIXES = ["/login", "/register", "/auth/", "/api/", "/logout", "/privacy", "/terms"];

function expireBrowserSession(response: NextResponse) {
  const expired = {
    path: "/",
    sameSite: "lax" as const,
    expires: new Date(0),
    maxAge: 0,
    secure: process.env.NODE_ENV === "production",
  };
  response.cookies.set("web_session", "", { ...expired, httpOnly: true });
  response.cookies.set("oauth_flow", "", { ...expired, httpOnly: true });
}

export function middleware(request: NextRequest) {
  const cspNonce = nonce();
  const csp = contentSecurityPolicy(cspNonce, process.env.NODE_ENV !== "production");
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", cspNonce);
  requestHeaders.set("Content-Security-Policy", csp);

  const path = request.nextUrl.pathname;
  const isPublic = PUBLIC_PREFIXES.some((prefix) => path === prefix || path.startsWith(prefix));
  const session = request.cookies.get("web_session")?.value;
  const signedOut = request.nextUrl.searchParams.get("signedout") === "1";

  let response: NextResponse;
  if (session && signedOut && path === "/login") {
    response = NextResponse.next({ request: { headers: requestHeaders } });
    expireBrowserSession(response);
  } else if (!session && !isPublic) {
    response = NextResponse.redirect(new URL("/login", request.url));
  } else if (session && (path === "/login" || path === "/login/start" || path === "/register")) {
    response = NextResponse.redirect(new URL("/", request.url));
  } else {
    response = NextResponse.next({ request: { headers: requestHeaders } });
  }

  if (!request.cookies.get("worthly_csrf")?.value) {
    response.cookies.set("worthly_csrf", csrfToken(), {
      httpOnly: false,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      path: "/",
      maxAge: 60 * 60 * 24 * 14,
    });
  }
  response.headers.set("Content-Security-Policy", csp);
  if (process.env.NODE_ENV === "production") {
    response.headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  }
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|icon.svg).*)"],
};
