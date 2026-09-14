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

const PUBLIC_PREFIXES = ["/login", "/register", "/auth/", "/api/", "/logout"];

export function middleware(request: NextRequest) {
  const cspNonce = nonce();
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", cspNonce);

  const path = request.nextUrl.pathname;
  const isPublic = PUBLIC_PREFIXES.some((prefix) => path === prefix || path.startsWith(prefix));
  const session = request.cookies.get("web_session")?.value;

  let response: NextResponse;
  if (!session && !isPublic) {
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
  response.headers.set(
    "Content-Security-Policy",
    contentSecurityPolicy(cspNonce, process.env.NODE_ENV !== "production"),
  );
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|icon.svg).*)"],
};
