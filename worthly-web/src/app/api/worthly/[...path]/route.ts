import { NextResponse } from "next/server";
import { apiFetch, csrfFromCookie, readSession, refreshTokens } from "@/lib/auth";
import { SESSION_COOKIE, sessionCookieOptions } from "@/lib/session";
import { encryptPayload } from "@/lib/session";

export async function GET(request: Request, context: { params: Promise<{ path?: string[] }> }) {
  return proxy(request, context, "GET");
}

export async function POST(request: Request, context: { params: Promise<{ path?: string[] }> }) {
  return proxy(request, context, "POST");
}

export async function PATCH(request: Request, context: { params: Promise<{ path?: string[] }> }) {
  return proxy(request, context, "PATCH");
}

export async function DELETE(request: Request, context: { params: Promise<{ path?: string[] }> }) {
  return proxy(request, context, "DELETE");
}

async function proxy(
  request: Request,
  context: { params: Promise<{ path?: string[] }> },
  method: string,
) {
  if (method !== "GET") {
    const csrf = await csrfFromCookie();
    const header = request.headers.get("x-csrf-token");
    if (!csrf || header !== csrf) {
      return NextResponse.json({ title: "Forbidden", detail: "csrf_invalid" }, { status: 403 });
    }
  }
  let session = await readSession();
  if (!session) {
    return NextResponse.json({ title: "Unauthorized" }, { status: 401 });
  }
  let refreshed: string | undefined;
  if (session.accessExpiresAt - Date.now() < 60_000) {
    try {
      session = await refreshTokens(session.refreshToken);
      refreshed = await encryptPayload(session);
    } catch {
      return NextResponse.json({ title: "Unauthorized" }, { status: 401 });
    }
  }
  const { path } = await context.params;
  const suffix = `/${(path ?? []).join("/")}`;
  const incomingUrl = new URL(request.url);
  const init: RequestInit = { method };
  if (method !== "GET" && method !== "DELETE") {
    init.body = await request.text();
    init.headers = { "Content-Type": request.headers.get("content-type") ?? "application/json" };
  }
  const upstream = await apiFetch(session, `${suffix}${incomingUrl.search}`, init);
  const body = await upstream.text();
  const headers = new Headers({
    "Content-Type": upstream.headers.get("content-type") ?? "application/json",
  });
  const disposition = upstream.headers.get("content-disposition");
  if (disposition) {
    headers.set("Content-Disposition", disposition);
  }
  const response = new NextResponse(body, {
    status: upstream.status,
    headers,
  });
  if (refreshed) {
    response.cookies.set(SESSION_COOKIE, refreshed, sessionCookieOptions);
  }
  return response;
}
