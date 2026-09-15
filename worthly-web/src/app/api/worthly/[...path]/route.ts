import { NextResponse } from "next/server";
import { apiFetch, csrfFromCookie, readSession, refreshTokens } from "@/lib/auth";
import { SESSION_COOKIE, encryptPayload, sessionCookieOptions, sessionStillValid } from "@/lib/session";

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
  if (!session || !sessionStillValid(session)) {
    return NextResponse.json({ title: "Unauthorized" }, { status: 401 });
  }
  let rolling = await encryptPayload(session);
  if (session.accessExpiresAt - Date.now() < 60_000) {
    try {
      session = await refreshTokens(session.refreshToken, session.issuedAt);
      rolling = await encryptPayload(session);
    } catch {
      if (session.accessExpiresAt <= Date.now()) {
        return NextResponse.json({ title: "Unauthorized" }, { status: 401 });
      }
    }
  }
  const { path } = await context.params;
  const suffix = `/${(path ?? []).join("/")}`;
  const incomingUrl = new URL(request.url);
  const init: RequestInit = { method };
  const outbound = new Headers();
  if (suffix.endsWith(".csv")) {
    outbound.set("Accept", "text/csv");
  }
  if (method !== "GET") {
    const text = await request.text();
    if (text.length > 0) {
      outbound.set("Content-Type", request.headers.get("content-type") ?? "application/json");
      init.body = text;
    }
  }
  if ([...outbound.keys()].length > 0) {
    init.headers = outbound;
  }
  const upstream = await apiFetch(session, `${suffix}${incomingUrl.search}`, init);
  const raw = await upstream.text();
  const noContent = upstream.status === 204 || upstream.status === 205 || upstream.status === 304;
  const inbound = new Headers();
  if (!noContent) {
    inbound.set("Content-Type", upstream.headers.get("content-type") ?? "application/json");
  }
  const disposition = upstream.headers.get("content-disposition");
  if (disposition) {
    inbound.set("Content-Disposition", disposition);
  }
  const response = new NextResponse(noContent || !raw ? null : raw, {
    status: upstream.status,
    headers: inbound,
  });
  if (rolling) {
    response.cookies.set(SESSION_COOKIE, rolling, sessionCookieOptions);
  }
  return response;
}
