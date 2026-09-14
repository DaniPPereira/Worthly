import { cookies } from "next/headers";
import { CSRF_COOKIE, decryptPayload, encryptPayload, OAUTH_FLOW_COOKIE, SESSION_COOKIE, type OauthFlow, type WebSession } from "./session";
import { config } from "./config";
import { encodeQuery } from "./pkce";

export async function readSession(): Promise<WebSession | null> {
  const jar = await cookies();
  const raw = jar.get(SESSION_COOKIE)?.value;
  if (!raw) {
    return null;
  }
  try {
    return await decryptPayload<WebSession>(raw);
  } catch {
    return null;
  }
}

export async function readOauthFlow(): Promise<OauthFlow | null> {
  const jar = await cookies();
  const raw = jar.get(OAUTH_FLOW_COOKIE)?.value;
  if (!raw) {
    return null;
  }
  try {
    return await decryptPayload<OauthFlow>(raw);
  } catch {
    return null;
  }
}

export async function csrfFromCookie(): Promise<string | undefined> {
  return (await cookies()).get(CSRF_COOKIE)?.value;
}

export async function parseFormCsrf(request: Request, cookieToken: string | undefined): Promise<void> {
  const clone = request.clone();
  const body = await clone.formData();
  const token = String(body.get("csrf") ?? "");
  if (!cookieToken || token !== cookieToken) {
    throw new Error("csrf_invalid");
  }
}

export async function encryptFlow(flow: OauthFlow): Promise<string> {
  return encryptPayload(flow);
}

export function tokenUrl(): string {
  return `${config.apiUrl}/oauth2/token`;
}

export function authorizeUrl(params: Record<string, string>): string {
  return `${config.issuer}/oauth2/authorize?${encodeQuery(params)}`;
}

export async function exchangeCode(code: string, verifier: string): Promise<WebSession> {
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    redirect_uri: `${config.origin}/auth/callback`,
    code_verifier: verifier,
  });
  return requestTokens(body);
}

let refreshInFlight: { token: string; promise: Promise<WebSession> } | null = null;

export async function refreshTokens(refreshToken: string): Promise<WebSession> {
  if (refreshInFlight && refreshInFlight.token === refreshToken) {
    return refreshInFlight.promise;
  }
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    refresh_token: refreshToken,
  });
  const promise = requestTokens(body).finally(() => {
    if (refreshInFlight?.promise === promise) {
      refreshInFlight = null;
    }
  });
  refreshInFlight = { token: refreshToken, promise };
  return promise;
}

async function requestTokens(body: URLSearchParams): Promise<WebSession> {
  const credentials = Buffer.from(`${config.clientId}:${config.clientSecret}`).toString("base64");
  const response = await fetch(tokenUrl(), {
    method: "POST",
    headers: {
      Authorization: `Basic ${credentials}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error("token_exchange_failed");
  }
  const json = (await response.json()) as {
    access_token: string;
    refresh_token: string;
    expires_in: number;
  };
  if (!json.access_token || !json.refresh_token) {
    throw new Error("token_exchange_failed");
  }
  return {
    accessToken: json.access_token,
    refreshToken: json.refresh_token,
    accessExpiresAt: Date.now() + json.expires_in * 1000,
    sessionId: crypto.randomUUID(),
  };
}

export async function apiFetch(session: WebSession, path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${session.accessToken}`);
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  return fetch(`${config.apiUrl}/api/v1${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });
}
