import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

function csrfToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

const PUBLIC_PREFIXES = ["/login", "/auth/", "/api/"];

export function middleware(request: NextRequest) {
  const response = NextResponse.next();
  if (!request.cookies.get("worthly_csrf")?.value) {
    response.cookies.set("worthly_csrf", csrfToken(), {
      httpOnly: false,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      path: "/",
      maxAge: 60 * 60 * 24 * 14,
    });
  }
  const path = request.nextUrl.pathname;
  const isPublic = PUBLIC_PREFIXES.some((prefix) => path === prefix || path.startsWith(prefix));
  const session = request.cookies.get("web_session")?.value;
  if (!session && !isPublic) {
    const login = new URL("/login", request.url);
    return NextResponse.redirect(login);
  }
  if (session && (path === "/login" || path === "/login/start")) {
    return NextResponse.redirect(new URL("/", request.url));
  }
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|icon.svg).*)"],
};
