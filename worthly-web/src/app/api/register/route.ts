import { NextResponse } from "next/server";
import { csrfFromCookie } from "@/lib/auth";
import { config } from "@/lib/config";

export async function POST(request: Request) {
  const csrf = await csrfFromCookie();
  const header = request.headers.get("x-csrf-token");
  if (!csrf || header !== csrf) {
    return NextResponse.json({ title: "Forbidden", detail: "csrf_invalid" }, { status: 403 });
  }
  const body = await request.text();
  const upstream = await fetch(`${config.apiUrl}/api/v1/register`, {
    method: "POST",
    headers: { "Content-Type": request.headers.get("content-type") ?? "application/json" },
    body,
  });
  return new NextResponse(await upstream.text(), {
    status: upstream.status,
    headers: { "Content-Type": upstream.headers.get("content-type") ?? "application/json" },
  });
}
